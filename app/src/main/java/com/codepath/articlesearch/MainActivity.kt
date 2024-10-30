package com.codepath.articlesearch

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.codepath.articlesearch.databinding.ActivityMainBinding
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Headers

private const val TAG = "MainActivity"
private const val SEARCH_API_KEY = BuildConfig.API_KEY
private const val ARTICLE_SEARCH_URL =
    "https://api.nytimes.com/svc/search/v2/articlesearch.json?api-key=$SEARCH_API_KEY"

fun createJson() = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = false
}

class MainActivity : AppCompatActivity() {
    private val articles = mutableListOf<DisplayArticle>()
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var networkReceiver: BroadcastReceiver
    private var isConnected = true


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use data binding to inflate the layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize RecyclerView
        setupRecyclerView()

        // Set up SwipeRefreshLayout
        setupSwipeRefresh()

        // Register network connectivity receiver
        registerNetworkReceiver()

        // Load initial data from the database
        loadArticlesFromDatabase()

        // Fetch new articles from the network
        fetchArticles()
    }

    private fun setupRecyclerView() {
        articleAdapter = ArticleAdapter(this, articles)
        binding.articles.apply {
            adapter = articleAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).also {
                addItemDecoration(DividerItemDecoration(context, it.orientation))
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeContainer.setOnRefreshListener {
            if (isConnected) {
                Log.d(TAG, "Refreshing data...")
                fetchArticles()
            } else {
                showOfflineSnackbar()
                binding.swipeContainer.isRefreshing = false
            }
        }
    }

    private fun registerNetworkReceiver() {
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        networkReceiver = NetworkReceiver { connected ->
            runOnUiThread {
                handleNetworkChange(connected)
            }
        }
        registerReceiver(networkReceiver, filter)
    }

    private fun loadArticlesFromDatabase() {
        lifecycleScope.launch {
            (application as ArticleApplication).db.articleDao().getAll().collect { databaseList ->
                databaseList.map { entity ->
                    DisplayArticle(
                        entity.headline,
                        entity.articleAbstract,
                        entity.byline,
                        entity.mediaImageUrl
                    )
                }.also { mappedList ->
                    articles.clear()
                    articles.addAll(mappedList)
                    articleAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun handleNetworkChange(connected: Boolean) {
        if (connected && !isConnected) {
            fetchArticles() // Network came back online, reload data
            Snackbar.make(binding.root, "Back online! Fetching new data...", Snackbar.LENGTH_SHORT).show()
        } else if (!connected && isConnected) {
            showOfflineSnackbar()
        }
        isConnected = connected
    }

    private fun showOfflineSnackbar() {
        Snackbar.make(binding.root, "You are offline. Please check your connection.", Snackbar.LENGTH_LONG).show()
    }

    private fun fetchArticles() {



        // Retrieve caching preference from SharedPreferences
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isCachingEnabled = sharedPreferences.getBoolean("cache_data", false)
        if (!isCachingEnabled) {
            Log.d(TAG, "Clearing the database as caching is disabled...")
            lifecycleScope.launch(IO) {
                (application as ArticleApplication).db.articleDao().deleteAll() // Clear all records
            }
        }
        if (isCachingEnabled) {
            Log.d(TAG, "Caching is enabled, attempting to load data from the database...")

            // Load articles from the database if caching is enabled
            lifecycleScope.launch {
                (application as ArticleApplication).db.articleDao().getAll().collect { databaseList ->
                    val mappedList = databaseList.map { entity ->
                        DisplayArticle(
                            entity.headline,
                            entity.articleAbstract,
                            entity.byline,
                            entity.mediaImageUrl
                        )
                    }

                    if (mappedList.isNotEmpty()) {
                        articles.clear()
                        articles.addAll(mappedList)
                        articleAdapter.notifyDataSetChanged()
                        Log.d(TAG, "Loaded cached articles from the database.")
                        return@collect
                    }
                }
            }
        } else {
            Log.d(TAG, "Caching is disabled, skipping database load.")
        }

        // Fetch new data from the API regardless of caching preference
        fetchFromNetwork(isCachingEnabled)
    }

    private fun fetchFromNetwork(isCachingEnabled: Boolean) {
        val client = AsyncHttpClient()
        client.get(ARTICLE_SEARCH_URL, object : JsonHttpResponseHandler() {
            override fun onFailure(statusCode: Int, headers: Headers?, response: String?, throwable: Throwable?) {
                Log.e(TAG, "Failed to fetch articles: $statusCode")
                Snackbar.make(binding.root, "Failed to fetch articles. Please try again later.", Snackbar.LENGTH_LONG).show()
                binding.swipeContainer.isRefreshing = false
            }

            override fun onSuccess(statusCode: Int, headers: Headers, json: JSON) {
                Log.d(TAG, "Successfully fetched articles: $json")
                try {
                    val parsedJson = createJson().decodeFromString(
                        SearchNewsResponse.serializer(),
                        json.jsonObject.toString()
                    )

                    parsedJson.response?.docs?.let { list ->
                        val newArticles = list.map {
                            ArticleEntity(
                                headline = it.headline?.main,
                                articleAbstract = it.abstract,
                                byline = it.byline?.original,
                                mediaImageUrl = it.mediaImageUrl
                            )
                        }

                        // Replace current data in the database only if caching is enabled
                        if (isCachingEnabled) {
                            lifecycleScope.launch(IO) {
                                val db = (application as ArticleApplication).db.articleDao()
                                db.deleteAll() // Clear existing data
                                db.insertAll(newArticles) // Insert new data
                                Log.d(TAG, "Database updated with new articles.")
                            }
                        }

                        // Update UI with the new data from the network
                        val displayArticles = newArticles.map {
                            DisplayArticle(
                                it.headline,
                                it.articleAbstract,
                                it.byline,
                                it.mediaImageUrl
                            )
                        }

                        articles.clear()
                        articles.addAll(displayArticles)
                        articleAdapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during JSON parsing: $e")
                } finally {
                    binding.swipeContainer.isRefreshing = false
                }
            }
        })
    }





    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(networkReceiver)
    }
}
