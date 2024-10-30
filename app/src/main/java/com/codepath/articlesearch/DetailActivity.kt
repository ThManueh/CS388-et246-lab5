package com.codepath.articlesearch

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

private const val TAG = "DetailActivity"

class DetailActivity : AppCompatActivity() {
    private lateinit var mediaImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var bylineTextView: TextView
    private lateinit var abstractTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        mediaImageView = findViewById(R.id.mediaImage)
        titleTextView = findViewById(R.id.mediaTitle)
        bylineTextView = findViewById(R.id.mediaByline)
        abstractTextView = findViewById(R.id.mediaAbstract)

        // Retrieve the DisplayArticle from the intent
        val article = intent.getSerializableExtra(ARTICLE_EXTRA) as? DisplayArticle

        // Set title, byline, and abstract information for the article
        article?.let {
            titleTextView.text = it.headline
            bylineTextView.text = it.byline
            abstractTextView.text = it.abstract

            // Load the media image
            Glide.with(this)
                .load(it.mediaImageUrl)
                .into(mediaImageView)
        }
    }
}
