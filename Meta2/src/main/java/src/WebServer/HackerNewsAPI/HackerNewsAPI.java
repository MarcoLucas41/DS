package src.WebServer.HackerNewsAPI;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is responsible for getting the top stories and the stories of a
 * given user from Hacker News API.
 * <p>
 */
public class HackerNewsAPI
{
    public List<String> getTopStoriesBySearchTerms(String searchTerms)
    {
        System.out.printf("Search Terms: %s\n",searchTerms);
        List<String> searchTermsUrls = new ArrayList<String>();
        String[] keywords = searchTerms.split(" ");
        try
        {
            // URL that returns the top stories
            URL url = new URL("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty");

            // Open the connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            // Read the response from the API

            inputLine = in.readLine();
            response.append(inputLine);

            // Close the connection
            in.close();
            connection.disconnect();

            String[] contentList = response.toString().split(", ");

            // Remove "[ " and " ]" from first and last element
            contentList[0] = contentList[0].substring(2);
            contentList[contentList.length - 1] = contentList[contentList.length - 1].substring(0,
                    contentList[contentList.length - 1].length() - 2);


            for(String word: keywords)
            {
                // Parse the JSON response
                List<String> urlList = jsonParser(contentList, word,false);
                System.out.println(urlList);
                for(String x: urlList)
                {
                    if(!searchTermsUrls.contains(x)) searchTermsUrls.add(x);
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }


        return searchTermsUrls;
    }


    /**
     * Returns a List object with the URLs of the stories
     * <p>
     * This method might take a while to run
     * <p>
     * Prints all the invalid URLs to the console
     * 
     * @param contentList String[] containing the ids of the stories
     * @return List of Strings containing the urls of the stories
     * @throws InterruptedException
     */
    private List<String> jsonParser(String[] contentList,String keyword, boolean user) throws InterruptedException
    {
        List<String> urlList = new ArrayList<String>(); // List of Strings containing the urls of the stories
        List<Thread> threads = new ArrayList<Thread>(); // List of Threads
        int numThreads = 10; // Number of threads to create

        List<String> invalidURLS = new ArrayList<String>(); // List of Strings containing stories with invalid URLs
        /*
        int numStories;
        if (user)
        {
            numStories = contentList.length; // Number of stories to get
        }
        else
        {
            numStories = 10; // Number of stories to get
        }
         */
        int numStories = contentList.length;

        for (int i = 0; i < numStories; i++)
        {
            final int index = i;

            Thread t = new Thread(() -> {
                try {
                    URL story = new URL(
                            "https://hacker-news.firebaseio.com/v0/item/" + contentList[index] + ".json?print=pretty");

                    // Open the connection
                    HttpURLConnection storyCon = (HttpURLConnection) story.openConnection();
                    storyCon.setRequestMethod("GET");

                    BufferedReader storyIn = new BufferedReader(new InputStreamReader(storyCon.getInputStream()));

                    JSONParser parser = new JSONParser();
                    String storyInputLine = "";
                    StringBuffer storyContent = new StringBuffer();

                    // Read the response from the API
                    while ((storyInputLine = storyIn.readLine()) != null) {
                        storyContent.append(storyInputLine);
                    }

                    // Parse the JSON response
                    if (storyContent.length() > 0)
                    {
                        JSONObject storyObject = (JSONObject) parser.parse(storyContent.toString());

                        if (storyObject.get("url") == null || storyObject.get("title") == null)
                        {
                            invalidURLS.add(storyObject.get("id").toString());
                            return;
                        }

                        // If the title contains the keyword, it will be added to be indexed later on
                        String title = storyObject.get("title").toString().toLowerCase();
                        System.out.println(title);
                        if(title.contains(keyword))
                        {
                            System.out.printf("This one has %s!\n",keyword);
                            urlList.add(storyObject.get("url").toString());
                        }

                    } else {
                        System.out.println("No story content");
                    }

                    // Close the connection
                    storyIn.close();
                    storyCon.disconnect();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads.add(t);
            t.start();

            // Limit the number of threads to avoid overloading the system
            if (threads.size() == numThreads) {
                for (Thread thread : threads) {
                    thread.join(); // Wait for each thread to finish
                }
                threads.clear();
            }
        }

        // Wait for the remaining threads to finish
        for (Thread thread : threads) {
            thread.join();
        }

        // Print the invalid URLs
        if (invalidURLS.size() > 0) {
            System.out.println("Invalid URLs:");
            System.out.println(invalidURLS.toString());
        }

        return urlList;
    }
}




