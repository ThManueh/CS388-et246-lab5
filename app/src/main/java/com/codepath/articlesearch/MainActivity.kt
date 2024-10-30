package com.codepath.articlesearch

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.codepath.articlesearch.databinding.ActivityMainBinding
import com.codepath.asynchttpclient.AsyncHttpClient
import com.codepath.asynchttpclient.callback.JsonHttpResponseHandler
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Headers

fun createJson() = Json {
    isLenient = true
    ignoreUnknownKeys = true
    useAlternativeNames = false
}

private const val TAG = "MainActivity"
private const val SEARCH_API_KEY = BuildConfig.API_KEY
private const val ARTICLE_SEARCH_URL =
    "https://api.nytimes.com/svc/search/v2/articlesearch.json?api-key=${SEARCH_API_KEY}"

class MainActivity : AppCompatActivity() {
    private val articles = mutableListOf<DisplayArticle>()
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use data binding to inflate the layout
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize RecyclerView and SwipeRefreshLayout using binding
        val articlesRecyclerView = binding.articles
        val swipeContainer = binding.swipeContainer

        // Initialize adapter and assign to RecyclerView
        articleAdapter = ArticleAdapter(this, articles)
        articlesRecyclerView.adapter = articleAdapter

        // Set up RecyclerView layout manager and decoration
        articlesRecyclerView.layoutManager = LinearLayoutManager(this).also {
            val dividerItemDecoration = DividerItemDecoration(this, it.orientation)
            articlesRecyclerView.addItemDecoration(dividerItemDecoration)
        }

        // Set up SwipeRefreshLayout
        swipeContainer.setOnRefreshListener {
            Log.d(TAG, "Refreshing data...")
            fetchArticles()
        }

        // Fetch initial articles from the API
        fetchArticles()
    }

    private fun fetchArticles() {
        val client = AsyncHttpClient()
        client.get(ARTICLE_SEARCH_URL, object : JsonHttpResponseHandler() {
            override fun onFailure(
                statusCode: Int,
                headers: Headers?,
                response: String?,
                throwable: Throwable?
            ) {
                Log.e(TAG, "Failed to fetch articles: $statusCode")
                binding.swipeContainer.isRefreshing = false // Stop the refreshing animation
            }

            override fun onSuccess(statusCode: Int, headers: Headers, json: JSON) {
                Log.d(TAG, "Successfully fetched articles: $json")

                try {
                    // Parse JSON using Kotlin Serialization
                    val parsedJson = createJson().decodeFromString(
                        SearchNewsResponse.serializer(),
                        json.jsonObject.toString()
                    )

                    // Clear existing data and update with new articles
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
                    binding.swipeContainer.isRefreshing = false // Stop the refreshing animation
                }
            }
        })
    }
}
