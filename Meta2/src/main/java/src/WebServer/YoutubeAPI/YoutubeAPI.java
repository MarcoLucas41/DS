package src.WebServer.YoutubeAPI;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class YoutubeAPI
{

    // Your Google Cloud API Key
    private static final String API_KEY = "GOOGLE_API_KEY";
    public List<String> getYoutubeVideosUrls(String keyword)
    {
        try
        {
            HttpTransport httpTransport = new NetHttpTransport();
            JsonFactory jsonFactory = new JacksonFactory();

            // Initialize the YouTube object
            YouTube youtube = new YouTube.Builder(httpTransport, jsonFactory, null)
                    .setApplicationName("youtube-search")
                    .build();

            // Define the search query
            YouTube.Search.List search = youtube.search().list("id");
            search.setKey(API_KEY);
            search.setQ(keyword);
            search.setType("video");
            search.setMaxResults(10L); // Adjust as needed

            // Execute the search
            SearchListResponse searchResponse = search.execute();
            List<SearchResult> searchResults = searchResponse.getItems();

            // Process the results
            List<String> videoUrls = new ArrayList<>();
            for (SearchResult searchResult : searchResults)
            {
                String videoId = searchResult.getId().getVideoId();
                String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
                videoUrls.add(videoUrl);
            }

            // Output the video URLs
            for (String url : videoUrls) {
                System.out.println(url);
            }
            return videoUrls;


        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
