package au.com.shiftyjelly.pocketcasts.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_STREAM
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.EXTRA_TITLE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import au.com.shiftyjelly.pocketcasts.analytics.AnalyticsTracker
import au.com.shiftyjelly.pocketcasts.analytics.SourceView
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode
import au.com.shiftyjelly.pocketcasts.repositories.images.PocketCastsImageRequestFactory
import au.com.shiftyjelly.pocketcasts.sharing.BuildConfig.SERVER_SHORT_URL
import au.com.shiftyjelly.pocketcasts.sharing.clip.Clip
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.Instagram
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.More
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.PocketCasts
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.Telegram
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.Tumblr
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.WhatsApp
import au.com.shiftyjelly.pocketcasts.sharing.social.SocialPlatform.X
import au.com.shiftyjelly.pocketcasts.sharing.timestamp.TimestampType
import au.com.shiftyjelly.pocketcasts.sharing.ui.CardType
import au.com.shiftyjelly.pocketcasts.utils.FileUtil
import coil.executeBlocking
import coil.imageLoader
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast as PodcastModel
import au.com.shiftyjelly.pocketcasts.models.entity.PodcastEpisode as EpisodeModel

class SharingClient(
    private val context: Context,
    private val mediaService: MediaService,
    tracker: AnalyticsTracker,
    private val displayPodcastCover: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
    private val showCustomCopyFeedback: Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2,
    private val hostUrl: String = SERVER_SHORT_URL,
    private val shareStarter: ShareStarter = object : ShareStarter {
        override fun start(context: Context, intent: Intent) {
            context.startActivity(intent)
        }

        override fun copyLink(context: Context, data: ClipData) {
            requireNotNull(context.getSystemService<ClipboardManager>()).setPrimaryClip(data)
        }
    },
) {
    private val imageRequestFactory = PocketCastsImageRequestFactory(context, isDarkTheme = false).smallSize()

    private val analytics = SharingAnalytics(tracker)

    suspend fun share(request: SharingRequest) = try {
        Timber.tag("SharingClient").i("Share: $request")
        analytics.logPodcastSharedEvent(request)
        request.tryShare()
    } catch (t: Throwable) {
        Timber.tag("SharingClient").e(t, "Failed to share a request: $request")
        SharingResponse(
            isSuccsessful = false,
            feedbackMessage = context.getString(LR.string.error),
        )
    }

    private suspend fun SharingRequest.tryShare(): SharingResponse = when (data) {
        is SharingRequest.Sociable -> when (platform) {
            Instagram -> {
                error("Not implemented yet")
            }
            PocketCasts -> {
                shareStarter.copyLink(context, ClipData.newPlainText(context.getString(data.linkDescription()), data.sharingUrl(hostUrl)))
                SharingResponse(
                    isSuccsessful = true,
                    feedbackMessage = if (showCustomCopyFeedback) context.getString(LR.string.share_link_copied_feedback) else null,
                )
            }
            WhatsApp, Telegram, X, Tumblr, More -> {
                Intent()
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(EXTRA_TEXT, data.sharingUrl(hostUrl))
                    .putExtra(EXTRA_TITLE, data.sharingTitle())
                    .setPackage(platform.packageId)
                    .addFlags(FLAG_GRANT_READ_URI_PERMISSION)
                    .setPodcastCover(data.podcast)
                    .share()
                SharingResponse(
                    isSuccsessful = true,
                    feedbackMessage = null,
                )
            }
        }
        is SharingRequest.Data.EpisodeFile -> {
            val file = data.episode.downloadedFilePath?.let(::File)
            if (file?.exists() == true) {
                Intent()
                    .setAction(Intent.ACTION_SEND)
                    .setType(data.episode.fileType)
                    .setExtraStream(file)
                    .share()
                SharingResponse(
                    isSuccsessful = true,
                    feedbackMessage = null,
                )
            } else {
                SharingResponse(
                    isSuccsessful = false,
                    feedbackMessage = context.getString(LR.string.error),
                )
            }
        }
        is SharingRequest.Data.ClipLink -> {
            shareStarter.copyLink(context, ClipData.newPlainText(context.getString(data.linkDescription()), data.sharingUrl(hostUrl)))
            SharingResponse(
                isSuccsessful = true,
                feedbackMessage = if (showCustomCopyFeedback) context.getString(LR.string.share_link_copied_feedback) else null,
            )
        }
        is SharingRequest.Data.ClipAudio -> when (platform) {
            Instagram -> {
                error("Not implemented yet")
            }
            WhatsApp, Telegram, X, Tumblr, PocketCasts, More -> {
                val file = mediaService.clipAudio(data.podcast, data.episode, data.range).getOrThrow()
                Intent()
                    .setAction(Intent.ACTION_SEND)
                    .setType("audio/mp3")
                    .setExtraStream(file)
                    .setPackage(platform.packageId)
                    .share()
                SharingResponse(
                    isSuccsessful = true,
                    feedbackMessage = null,
                )
            }
        }
        is SharingRequest.Data.ClipVideo -> when (platform) {
            Instagram -> {
                error("Not implemented yet")
            }
            WhatsApp, Telegram, X, Tumblr, PocketCasts, More -> {
                val file = mediaService.clipVideo(data.podcast, data.episode, data.range, data.backgroundImage).getOrThrow()
                Intent()
                    .setAction(Intent.ACTION_SEND)
                    .setType("video/mp4")
                    .setExtraStream(file)
                    .setPackage(platform.packageId)
                    .share()
                SharingResponse(
                    isSuccsessful = true,
                    feedbackMessage = null,
                )
            }
        }
    }

    private fun Intent.share() {
        shareStarter.start(context, toChooserIntent())
    }

    private fun Intent.toChooserIntent() = Intent
        .createChooser(this, context.getString(LR.string.podcasts_share_via))
        .addFlags(FLAG_ACTIVITY_NEW_TASK)

    private suspend fun Intent.setPodcastCover(podcast: Podcast) = apply {
        if (displayPodcastCover) {
            val coverUri = withContext(Dispatchers.IO) {
                runCatching {
                    val request = imageRequestFactory.create(podcast)
                    context.imageLoader.executeBlocking(request).drawable?.toBitmap()?.let { bitmap ->
                        val imageFile = File(context.cacheDir, "share_podcast_thumbnail.jpg")
                        FileOutputStream(imageFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
                        FileUtil.getUriForFile(context, imageFile)
                    }
                }.getOrNull()
            }
            if (coverUri != null) {
                clipData = ClipData.newRawUri(null, coverUri)
            }
        }
    }

    private fun Intent.setExtraStream(file: File) = putExtra(EXTRA_STREAM, FileUtil.createUriWithReadPermissions(context, file, this))
}

data class SharingRequest internal constructor(
    internal val data: SharingRequest.Data,
    internal val platform: SocialPlatform,
    internal val cardType: CardType?,
    internal val source: SourceView,
) {
    companion object {
        fun podcast(
            podcast: PodcastModel,
        ) = Builder(Data.Podcast(podcast))

        fun episode(
            podcast: PodcastModel,
            episode: PodcastEpisode,
        ) = Builder(Data.Episode(podcast, episode))

        fun episodePosition(
            podcast: PodcastModel,
            episode: PodcastEpisode,
            position: Duration,
        ) = Builder(Data.EpisodePosition(podcast, episode, position, TimestampType.Episode))

        fun bookmark(
            podcast: PodcastModel,
            episode: PodcastEpisode,
            position: Duration,
        ) = Builder(Data.EpisodePosition(podcast, episode, position, TimestampType.Bookmark))

        fun episodeFile(
            podcast: Podcast,
            episode: PodcastEpisode,
        ) = Builder(Data.EpisodeFile(podcast, episode))

        fun clipLink(
            podcast: Podcast,
            episode: PodcastEpisode,
            range: Clip.Range,
        ) = Builder(Data.ClipLink(podcast, episode, range))

        fun audioClip(
            podcast: Podcast,
            episode: PodcastEpisode,
            range: Clip.Range,
        ) = Builder(Data.ClipAudio(podcast, episode, range))

        fun videoClip(
            podcast: Podcast,
            episode: PodcastEpisode,
            range: Clip.Range,
            backgroundImage: File,
        ) = Builder(Data.ClipVideo(podcast, episode, range, backgroundImage))
    }

    class Builder internal constructor(
        private var data: SharingRequest.Data,
    ) {
        private var platform = More
        private var cardType: CardType? = null
        private var source = SourceView.UNKNOWN

        fun setPlatform(platform: SocialPlatform) = apply {
            this.platform = platform
        }

        fun setCardType(cardType: CardType) = apply {
            this.cardType = cardType
        }

        fun setSourceView(source: SourceView) = apply {
            this.source = source
        }

        fun build() = SharingRequest(
            data = data,
            platform = platform,
            cardType = cardType,
            source = source,
        )
    }

    internal sealed interface Sociable {
        fun sharingUrl(host: String): String

        fun sharingTitle(): String

        @StringRes fun linkDescription(): Int
    }

    internal sealed interface Data {
        val podcast: PodcastModel

        data class Podcast(
            override val podcast: PodcastModel,
        ) : Data, Sociable {
            override fun sharingUrl(host: String) = "$host/podcast/${podcast.uuid}"

            override fun sharingTitle() = podcast.title

            override fun linkDescription() = LR.string.share_link_podcast

            override fun toString() = "Podcast(title=${podcast.title}, uuid=${podcast.uuid})"
        }

        data class Episode(
            override val podcast: PodcastModel,
            val episode: EpisodeModel,
        ) : Data, Sociable {
            override fun sharingUrl(host: String) = "$host/episode/${episode.uuid}"

            override fun sharingTitle() = episode.title

            override fun linkDescription() = LR.string.share_link_episode

            override fun toString() = "Episode(title=${episode.title}, uuid=${episode.uuid})"
        }

        data class EpisodePosition(
            override val podcast: PodcastModel,
            val episode: EpisodeModel,
            val position: Duration,
            val type: TimestampType,
        ) : Data, Sociable {
            override fun sharingUrl(host: String) = "$host/episode/${episode.uuid}?t=${position.inWholeSeconds}"

            override fun sharingTitle() = episode.title

            override fun linkDescription() = when (type) {
                TimestampType.Episode -> LR.string.share_link_episode_position
                TimestampType.Bookmark -> LR.string.share_link_bookmark
            }

            override fun toString() = "EpisodePosition(title=${episode.title}, uuid=${episode.uuid}, position=$position, type=$type)"
        }

        data class EpisodeFile(
            override val podcast: PodcastModel,
            val episode: EpisodeModel,
        ) : Data {
            override fun toString() = "EpisodeFile(title=${episode.title}, uuid=${episode.uuid}"
        }

        data class ClipLink(
            override val podcast: PodcastModel,
            val episode: EpisodeModel,
            val range: Clip.Range,
        ) : Data {
            fun sharingUrl(host: String) = "$host/episode/${episode.uuid}?t=${range.startInSeconds},${range.endInSeconds}"

            fun linkDescription() = LR.string.share_link_clip

            override fun toString() = "ClipLink(title=${episode.title}, uuid=${episode.uuid}, start=${range.startInSeconds}, end=${range.endInSeconds})"
        }

        data class ClipAudio(
            override val podcast: PodcastModel,
            val episode: EpisodeModel,
            val range: Clip.Range,
        ) : Data {
            override fun toString() = "ClipAudio(title=${episode.title}, uuid=${episode.uuid}, start=${range.startInSeconds}, end=${range.endInSeconds})"
        }

        data class ClipVideo(
            override val podcast: PodcastModel,
            val episode: EpisodeModel,
            val range: Clip.Range,
            val backgroundImage: File,
        ) : Data {
            override fun toString() = "ClipVideo(title=${episode.title}, uuid=${episode.uuid}, start=${range.startInSeconds}, end=${range.endInSeconds})"
        }
    }
}

data class SharingResponse(
    val isSuccsessful: Boolean,
    val feedbackMessage: String?,
)
