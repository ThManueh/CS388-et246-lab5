package com.codepath.articlesearch

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.codepath.articlesearch.databinding.ActivityMainBinding
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler
import com.google.android.material.snackbar.Snackbar
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

        // Fetch initial articles
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

    private fun handleNetworkChange(connected: Boolean) {
        if (connected && !isConnected) {
            fetchArticles()  // Network came back online, reload data
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
        val client = AsyncHttpClient()
        client.get(ARTICLE_SEARCH_URL, object : JsonHttpResponseHandler() {
            override fun onFailure(statusCode: Int, headers: Headers?, response: String?, throwable: Throwable?) {
                Log.e(TAG, "Failed to fetch articles: $statusCode")
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
                        val displayArticles = list.map {
                            DisplayArticle(
                                headline = it.headline?.main,
                                abstract = it.abstract,
                                byline = it.byline?.original,
                                mediaImageUrl = it.mediaImageUrl
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
