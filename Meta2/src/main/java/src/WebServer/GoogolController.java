package src.WebServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import src.RMIInterface.RMIGatewayInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import src.WebServer.HackerNewsAPI.HackerNewsAPI;
import src.WebServer.YoutubeAPI.YoutubeAPI;


@Controller
public class GoogolController
{
    private RMIGatewayInterface gatewayInterface;
    private HackerNewsAPI hackerNewsAPI;

    private YoutubeAPI youtubeAPI;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    public GoogolController(RMIGatewayInterface gatewayInterface)
    {
        this.gatewayInterface = gatewayInterface;
        this.hackerNewsAPI = new HackerNewsAPI();
        this.youtubeAPI = new YoutubeAPI();
    }


    /**
     * Given keywords typed in input box from "/Youtube", it redirects to view that showcases the youtube search results
     * @param query Keywords from input box
     * @param model Model
     * @return Redirect to view that showcases the youtube search results
     */
    @GetMapping("/Youtube")
    public String searchYoutube(@RequestParam(name = "query", required = false, defaultValue = "") String query,
    Model model)
    {
        if (query.isEmpty())
        {
            // Returns "youtube" view template, if there's nothing on the query
            return "youtube";
        }
        System.out.println("prompt = " + query);

        try 
        {
            //List<String> response = youtubeAPI.getYoutubeVideosUrls(query);
            String message = "Youtube URLs retrieved!";
            model.addAttribute("results", message);
        } catch (Exception e) {
            System.out.println("Error connecting to server through '/Youtube'");
        }
        return "redirect:/getYoutubeResults/" + query + "?page=0";
    }

    /**
     * Given search terms, it indexes hacker news stories with terms in the title
     * @param queryParam Search terms
     * @return Response view
     */
    @PostMapping("/indexHackersNews")
    public String IndexHackersNews(@RequestParam String queryParam)
    {
        List<String> results = new ArrayList<String>();
        System.out.printf("Search Terms: %s\n",queryParam);
        try
        {
            results = hackerNewsAPI.getTopStoriesBySearchTerms(queryParam);
            if (results.isEmpty())
            {
                return "menu";
            }
            System.out.println(results);
            for (String url : results)
            {
                boolean searching = true;
                while (searching) {
                    try {
                        gatewayInterface.indexNewURL(url);
                        searching = false;
                    } catch (Exception e) {
                        searching = true;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            //model.addAttribute("hackerNewsResult", "Error indexing Hacker News stories");
            return "error";
        }
        return "menu";
    }

    /**
     * Given a username, it indexes into the barrels the stories from Hacker News published by that username
     * @param username Username input
     * @param model Model
     * @return Response view
     */
    @GetMapping("/IndexHackersByUsername")
    public String IndexHackersByUsername(
            @RequestParam(name = "username", required = false, defaultValue = "") String username, Model model)
    {
        List<String> results = new ArrayList<String>();
        model.addAttribute("justClicked", true);

        if (username == null || username.isEmpty()) {
            return "IndexHackersByUsername";
        }

        try {
            results = hackerNewsAPI.getUserStories(username);

            if (results == null || results.isEmpty()) {
                model.addAttribute("results", false);
                model.addAttribute("justClicked", false);
                model.addAttribute("hacker", username);
                return "IndexHackersByUsername";
            }

            for (String url : results)
            {
                boolean searching = true;
                while (searching) {
                    try {
                        gatewayInterface.indexNewURL(url);
                        searching = false;
                    } catch (Exception e) {
                        searching = true;
                    }
                }
                gatewayInterface.indexNewURL(url);
            }

            model.addAttribute("justClicked", false);
            model.addAttribute("results", true);
            model.addAttribute("hacker", username);
            System.out.println("results = " + results);

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        return "IndexHackersByUsername";
    }

    /**
     * Given a URL, it is indexed into the RMIBarrels
     * @param url Url to be indexed
     * @param model Model
     * @return Response view
     * @throws RemoteException
     */
    @GetMapping("/indexNewUrl")
    public String indexNewUrl(@RequestParam(name = "url", required = false, defaultValue = "") String url,
            Model model) throws RemoteException
    {

        if (url.isEmpty())
        {
            // Returns "indexNewUrl" view template, if there's nothing on the query
            return "indexNewUrl";
        }
        System.out.println("url = " + url);
        messagingTemplate.convertAndSend("/topic/admin", new Message(convertToJSON(gatewayInterface.getAdminMenu())));
        try
        {
            gatewayInterface.indexNewURL(url);
            String message = "URL has been indexed to queue";
            model.addAttribute("results", message);
        } catch (Exception e) {
            System.out.println("Error connecting to server through '/indexNewUrl'");
        }

        return "indexNewUrl";
    }

    /**
     * Given a URL, it retrieves all hyperlinks that point to the URL
     * @param url URL input
     * @param model Model
     * @return Response view containing the hyperlinks
     */
    @GetMapping("/listPages")
    public String listPages(@RequestParam(name = "url", required = false, defaultValue = "") String url, Model model)
    {

        model.addAttribute("hasInfo", false);

        if (url.isEmpty()) {
            return "listPages";
        }

        System.out.println("url to list = " + url);

        try
        {
            List<String> results = gatewayInterface.searchLinks(url);

            String resultsString = results.toString();


            // Replace the first character "[" with "\""
            resultsString = resultsString.replaceFirst("\\[", "");

            // Replace the last character "]" with "\""
            resultsString = resultsString.substring(0, resultsString.length() - 1);

            resultsString = resultsString.replace(", ", "<br>");

            System.out.println("resultsString = " + resultsString);

            model.addAttribute("hasInfo", true);

            // If there are no search results
            if (results.isEmpty())
            {
                model.addAttribute("hasResults", false);
                return "listPages";
            }

            model.addAttribute("hasResults", true);
            model.addAttribute("results", resultsString);
        } catch (Exception e) {
            System.out.println("Error connecting to server through '/listPages'");
        }

        return "listPages";
    }



    @MessageMapping("/hello")
    @SendTo("/topic/admin")
    public void greeting() throws Exception
    {
        scheduler.scheduleAtFixedRate(this::sendMessage, 0, 1, TimeUnit.SECONDS);
    }

    private void sendMessage()
    {
        try
        {
            String s = convertToJSON(gatewayInterface.getAdminMenu());
            Message message = new Message(s);
            messagingTemplate.convertAndSend("/topic/admin", message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/")
    public String root(Model model)
    {
        return "menu";
    }


    public static void printJSON(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Object jsonObject = objectMapper.readValue(json, Object.class);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            System.out.println(prettyJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Given a string input, it creates a Json object from which the format is then returned back into a string
     * @param input Input string
     * @return String containing admin page information
     */
    public static String convertToJSON(String input)
    {
        List<String> downloaders = new ArrayList<>();
        List<String> barrels = new ArrayList<>();
        List<String> searches = new ArrayList<>();

        // Parse the input string and extract the relevant information
        String[] lines = input.split("\n");
        int state = 0;
        // 0 - Downloaders
        // 1 - Barrels
        // 2 - Most Frequent Searches

        for (String line : lines)
        {
            if (line.startsWith("------- Downloaders -------")) state = 0;
            else if (line.startsWith("------- Barrels -------")) state = 1;
            else if (line.startsWith("------- Most Frequent Searches -------")) state = 2;
            else if (!line.isEmpty())
            {
                switch (state)
                {
                    case 0:
                        downloaders.add(line);
                        break;
                    case 1:
                        barrels.add(line);
                        break;
                    case 2:
                        searches.add(line);
                        break;
                }
            }
        }

        // Create the JSON object using Jackson
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode json = objectMapper.createObjectNode();

        // Add the downloader information
        json.put("num_downloaders", downloaders.size());
        ArrayNode downloaderStates = objectMapper.createArrayNode();
        for (String downloader : downloaders) {
            downloaderStates.add(downloader);
        }
        json.set("downloader_states", downloaderStates);

        // Add the barrel information
        json.put("num_barrels", barrels.size());
        ArrayNode barrelStates = objectMapper.createArrayNode();
        for (String barrel : barrels) {
            barrelStates.add(barrel);
        }
        json.set("barrel_states", barrelStates);

        // Add the search information
        json.put("num_searches", searches.size());
        ArrayNode searchStates = objectMapper.createArrayNode();
        for (String search : searches) {
            searchStates.add(search);
        }
        json.set("search_states", searchStates);

        String result;
        try {
            result = objectMapper.writeValueAsString(json);
            //printJSON(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // Convert the JSON object to a string
        try {
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Handler for getting and displaying Googol search results
     * @param model Model
     * @param query String with the value passed by input box
     * @param page Number of the page
     * @return String with the results
     * @throws Exception
     */
    @GetMapping("getSearchResults/{query}")
    public String getSearchResults(Model model, @PathVariable String query, @RequestParam(defaultValue = "0") int page)
            throws Exception
    {
        List<String> strings = new ArrayList<>();
        List<String> aux = gatewayInterface.searchWords(query);

        if (aux.isEmpty())
        {
            model.addAttribute("noResults", true);
            model.addAttribute("results", "No results!");
            return "getSearchResults";
        }

        int startIndex = page * 10;
        int endIndex = Math.min(startIndex + 10, aux.size());

        for (int i = startIndex; i < endIndex; i++) {
            String s = aux.get(i);
            strings.add(s);
        }
        for(String s: strings)
        {
            System.out.println(s);
        }


        String resultsString = String.join(",", strings);
        resultsString = resultsString.replace(",", "<br><br>");
        resultsString = resultsString.replace(";", "<br>");
        System.out.println(resultsString);

        model.addAttribute("results", resultsString);

        return "getSearchResults";
    }


    /**
     * Handler for getting and displaying youtube search results
     * @param model Model
     * @param query String with the value passed by input box
     * @param page Number of the page
     * @return String with the results
     * @throws Exception
     */
    @GetMapping("getYoutubeResults/{query}")
    public String getYoutubeResults(Model model, @PathVariable String query, @RequestParam(defaultValue = "0") int page)
            throws Exception
    {
        List<String> strings = new ArrayList<>();
        System.out.println(query);
        List<String[]> aux = youtubeAPI.getYoutubeVideosUrls(query);

        messagingTemplate.convertAndSend("/topic/admin", new Message(convertToJSON(gatewayInterface.getAdminMenu())));

        if (aux.isEmpty())
        {
            System.out.println("no results");
            model.addAttribute("noResults", true);
            model.addAttribute("results", "No results!");
            return "getYoutubeResults";
        }

        for(String[] info: aux)
        {
            String s = info[0] + ";" + info[1] + ";" + info[2];
            System.out.println(s);
            strings.add(s);
        }


        String resultsString = String.join(",", strings);
        resultsString = resultsString.replace(",", "<br><br>");
        resultsString = resultsString.replace(";", "<br>");

        model.addAttribute("results", resultsString);

        return "getYoutubeResults";
    }
}
