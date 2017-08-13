package com.nononsenseapps.feeder.model;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.nononsenseapps.feeder.ui.text.HtmlToPlainTextConverter;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndLink;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.joda.time.DateTime;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.nononsenseapps.feeder.util.HelpersKt.getAbsoluteUrl;
import static java.lang.Math.min;

public class FeedParser {

    // Should reuse same instance to have same cache
    private static OkHttpClient _client;

    private static OkHttpClient cachingClient(File cacheDirectory) {
        if (_client != null) {
            return _client;
        }

        int cacheSize = 10 * 1024 * 1024; // 10 MiB
        Cache cache = new Cache(cacheDirectory, cacheSize);

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS);

        trustAllCerts(builder);

        _client = builder.build();

        return _client;
    }

    private static void trustAllCerts(OkHttpClient.Builder builder) {
        try {
            X509TrustManager trustManager = new X509TrustManager() {
                @SuppressLint("TrustAllX509TrustManager")
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @SuppressLint("TrustAllX509TrustManager")
                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            builder.sslSocketFactory(sslSocketFactory, trustManager)
                   .hostnameVerifier(new HostnameVerifier() {
                       @Override
                       public boolean verify(String hostname, SSLSession session) {
                           return true;
                       }
                   });
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    public static SyndFeed parseFeed(String url, File cacheDir) throws FeedParsingError {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = cachingClient(cacheDir).newCall(request).execute();

            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            Log.d("RSSLOCAL", "cache response: " + response.cacheResponse());
            Log.d("RSSLOCAL", "network response: " + response.networkResponse());

            return parseFeed(response.body().source().inputStream());
        } catch (Throwable e) {
            throw new FeedParsingError(e);
        }
    }

    static SyndFeed parseFeed(InputStream is) throws FeedParsingError {
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(is));

            for (SyndEntry entry: feed.getEntries()) {
                if (entry.getAuthors().isEmpty()) {
                    entry.setAuthors(feed.getAuthors());
                }
            }

            return feed;
        } catch (Throwable e) {
            throw new FeedParsingError(e);
        }
    }

    @Nullable
    public static String firstEnclosure(SyndEntry entry) {
        if (!entry.getEnclosures().isEmpty()) {
            SyndEnclosure enclosure = entry.getEnclosures().get(0);
            if (enclosure.getUrl() != null) {
                return enclosure.getUrl();
            }
        }

        return null;
    }

    @Nullable
    public static String publishDate(SyndEntry entry) {
        if (entry.getPublishedDate() != null) {
            return new DateTime(entry.getPublishedDate().getTime()).toDateTimeISO().toString();
        }
        // This is the required element in atom feeds
        if (entry.getUpdatedDate() != null) {
            return new DateTime(entry.getUpdatedDate().getTime()).toDateTimeISO().toString();
        }
        return null;
    }

    @NonNull
    public static String title(SyndEntry entry) {
        return nonNullString(entry.getTitle());
    }

    @NonNull
    public static String plainTitle(SyndEntry entry) {
        return HtmlToPlainTextConverter.HtmlToPlainText(title(entry));
    }

    @NonNull
    public static String description(SyndEntry entry) {
        // Atom
        if (!entry.getContents().isEmpty()) {
            List<SyndContent> contents = entry.getContents();
            SyndContent content = null;

            // In case of multiple contents, prioritize html
            for (SyndContent c: contents) {
                if (content == null) {
                    content = c;
                } else if ("html".equalsIgnoreCase(content.getType()) ||
                        "xhtml".equalsIgnoreCase(content.getType())) {
                    // Already html
                    break;
                } else if ("html".equalsIgnoreCase(c.getType()) ||
                        "xhtml".equalsIgnoreCase(c.getType())) {
                    content = c;
                    break;
                }
            }

            return nonNullString(content.getValue());
        }

        // Rss
        if (entry.getDescription() != null) {
            return nonNullString(entry.getDescription().getValue());
        }

        // In case of faulty feed
        return "";
    }

    /**
     *
     * @return null in case no self links exist - which is true for some RSS feeds
     */
    @Nullable
    public static String selfLink(SyndFeed feed) {
        // entry.getUri() can return bad data in case of atom feeds where it returns the ID element
        for (SyndLink link : feed.getLinks()) {
            if ("self".equalsIgnoreCase(link.getRel())) {
                return link.getHref();
            }
        }

        return null;
    }

    @NonNull
    public static String snippet(SyndEntry entry) {
        String text = HtmlToPlainTextConverter.HtmlToPlainText(FeedParser.description(entry));
        return text.substring(0, min(200, text.length()));
    }

    @Nullable
    public static String thumbnail(SyndEntry entry) {
        MediaEntryModule media = (MediaEntryModule) entry.getModule(MediaModule.URI);
        if (media != null) {
            Thumbnail[] thumbnails = media.getMetadata().getThumbnail();
            if (thumbnails != null && thumbnails.length > 0) {
                return getAbsoluteUrl(thumbnails[0].getUrl().toString());
            }
            // Fallback to images
            MediaContent[] contents = media.getMediaContents();
            if (contents != null && contents.length > 0) {
                for (MediaContent content: contents) {
                    if ("image".equalsIgnoreCase(content.getMedium())) {
                        return getAbsoluteUrl(content.getReference().toString());
                    }
                }
            }
        }

        return null;
    }

    public static class FeedParsingError extends Exception {
        public FeedParsingError(Throwable e) {
            super(e);
        }
    }

    @NonNull
    private static String nonNullString(@Nullable String text) {
        return text == null ? "" : text;
    }
}
