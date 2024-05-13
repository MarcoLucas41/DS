package src.WebServer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import src.RMIInterface.RMIGatewayInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theokanning.openai.client.OpenAiApi;

import src.WebServer.HackerNewsAPI.HackerNewsAPI;
import src.WebServer.OpenAI.AiRequestDTO;
import src.WebServer.OpenAI.AiService;

@Controller
public class GoogolController
{
    private RMIGatewayInterface gatewayInterface;
    private HackerNewsAPI hackerNewsAPI;
    private AiService openai;

    private boolean userLogged = false;
    private String username;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    public GoogolController(RMIGatewayInterface gatewayInterface)
    {
        this.gatewayInterface = gatewayInterface;
        this.hackerNewsAPI = new HackerNewsAPI();
        this.openai = new AiService();
    }


    @GetMapping("/OpenAI")
    public String askGPT(@RequestParam(name = "prompt", required = false, defaultValue = "") String prompt,
    Model model)
    {

        if (prompt.isEmpty())
        {
            // Returns "openai" view template, if there's nothing on the query
            return "openai";
        }
        System.out.println("prompt = " + prompt);

        try 
        {
            List<String> response = openai.generateText(new AiRequestDTO(prompt));
            String message = "OpenAI 200";
            model.addAttribute("results", message);
        } catch (Exception e) {
            System.out.println("Error connecting to server through '/openai'");
        }
        return "openai";
    }







    // When the button is pressed, the form is submitted and we print the result in
    // the ${query} variable
    @GetMapping("/search")
    public String search(@RequestParam(name = "query", required = false, defaultValue = "") String query,
            Model model) throws Exception {

        if (query.isEmpty())
        {
            // Returns "search" view template, if there's nothing on the query
            return "search";
        }

        // Sends the message to all clients connected to "/topic/admin"
        messagingTemplate.convertAndSend("/topic/admin", new Message(convertToJSON(gatewayInterface.getAdminMenu())));

        try
        {
            List<String> results = gatewayInterface.searchWords(query);
            model.addAttribute("results", results);
        }
        catch (Exception e)
        {
            System.out.println("Error connecting to server through '/search'");
        }

        return "redirect:/getSearchResults/" + query + "?page=0";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
    @GetMapping("/indexNewUrl")
    public String indexNewUrl(@RequestParam(name = "url", required = false, defaultValue = "") String url,
            Model model) {

        if (url.isEmpty())
        {
            // Returns "indexNewUrl" view template, if there's nothing on the query
            return "indexNewUrl";
        }
        System.out.println("url = " + url);

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

    @GetMapping("/listPages")
    public String listPages(@RequestParam(name = "url", required = false, defaultValue = "") String url, Model model)
    {

        if (!this.userLogged) {
            return "redirect:/login";
        }

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

    @GetMapping("/IndexHackersByUsername")
    public String IndexHackersByUsername(
            @RequestParam(name = "username", required = false, defaultValue = "") String username, Model model)
    {
        List<String> results;

        model.addAttribute("justClicked", true);

        if (username == null || username.isEmpty()) {
            return "IndexHackersByUsername";
        }

        try {
            results = hackerNewsAPI.getUserStories(username);

            if (results == null || results.isEmpty())
            {
                model.addAttribute("results", false);
                model.addAttribute("justClicked", false);
                model.addAttribute("hacker", username);
                return "IndexHackersByUsername";
            }

            for (String url : results)
            {
                boolean searching = true;
                while (searching)
                {
                    try
                    {
                        gatewayInterface.indexNewURL(url);
                        searching = false;
                    }
                    catch (Exception e)
                    {
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

    @GetMapping("/IndexHackersNews")
    public String IndexHackersNews(Model model) {
        List<String> results = new ArrayList<String>();

        model.addAttribute("hackerNewsResult", "Indexing Hacker News top stories...");

        try {
            results = hackerNewsAPI.getTopStories();

            if (results.isEmpty() || results == null) {
                model.addAttribute("hackerNewsResult", "Error getting top stories from Hacker News!");
                return "/";
            }

            for (String url : results) {
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
            model.addAttribute("hackerNewsResult", "Ocorreu um erro ao indexar os top stories do Hacker News");
            return "error";
        }

        model.addAttribute("hackerNewsBoolean", true);
        model.addAttribute("hackerNewsResult", "Top stories from Hacker News indexed with success!");
        model.addAttribute("username", this.username);
        model.addAttribute("userLogged", this.userLogged);
        return "menu";
    }

    @MessageMapping("/hello")
    @SendTo("/topic/admin")
    public void greeting() throws Exception {
        scheduler.scheduleAtFixedRate(this::sendMessage, 0, 1, TimeUnit.SECONDS);
    }

    private void sendMessage() {
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
    public String root(Model model) {
        model.addAttribute("userLogged", this.userLogged);
        model.addAttribute("username", this.username);
        return "menu";
    }

    @GetMapping("/register")
    public String register(Model model)
    {
        if (this.userLogged) {
            return "redirect:/";
        }

        String username = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest()
                .getParameter("username");
        String password = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest()
                .getParameter("password");

        if (username == null || password == null) {
            return "register";
        }

        File file = new File("src\\main\\java\\src\\WebServer\\login.txt");

        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println("Error creating login file: " + e.getMessage());
        }

        //Checks if username is already taken
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(";");

                if (parts[0].equals(username)) {
                    model.addAttribute("results", "Username already exists!");
                    return "register";
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error reading login file: " + e.getMessage());
        }

        try (FileWriter fileWriter = new FileWriter(file, true);
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                PrintWriter out = new PrintWriter(bufferedWriter))
        {
            out.println(username + ";" + password);
        } catch (IOException e) {
            System.out.println("Error writing to login file: " + e.getMessage());
        }

        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout() {
        this.userLogged = false;
        return "redirect:/";
    }

    @GetMapping("/login")
    public String login()
    {

        if (this.userLogged) {
            return "redirect:/";
        }

        String username = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest()
                .getParameter("username");
        String password = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest()
                .getParameter("password");

        if (username == null || password == null) {
            return "login";
        }

        File file = new File("src\\main\\java\\src\\WebServer\\login.txt");

        try {
            file.createNewFile();
        } catch (IOException e) {
            System.out.println("Error creating login file: " + e.getMessage());
        }

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(";");

                if (parts[0].equals(username) && parts[1].equals(password))
                {
                    System.out.println("Login successful!");
                    this.userLogged = true;
                    this.username = username;
                    return "redirect:/";
                }
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("Login failed!");
        return "redirect:/login";
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

    public static String convertToJSON(String input)
    {
        List<String> downloaders = new ArrayList<>();
        List<String> barrels = new ArrayList<>();
        List<String> searches = new ArrayList<>();

        // Parse the input string and extract the relevant information
        String[] lines = input.split("\n");
        int state = 0; // 0 - Downloaders, 1 - Barrels, 2 - Most Frequent Searches

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
            printJSON(result);
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

    @GetMapping("getSearchResults/{query}")
    public String getSearchResults(Model model, @PathVariable String query, @RequestParam(defaultValue = "0") int page)
            throws Exception
    {
        List<String> strings = new ArrayList<>();
        List<String> aux = gatewayInterface.searchWords(query);

        // Envie a mensagem para os clientes conectados ao tópico "/topic/admin"
        messagingTemplate.convertAndSend("/topic/admin", new Message(convertToJSON(gatewayInterface.getAdminMenu())));

        if (aux.size() == 0) {
            model.addAttribute("noResults", true);
            model.addAttribute("results", "Nenhum resultado encontrado!");
            return "getSearchResults";
        }

        int startIndex = page * 10;
        int endIndex = Math.min(startIndex + 10, aux.size());

        for (int i = startIndex; i < endIndex; i++) {
            String s = aux.get(i);
            strings.add(s);
        }

        String resultsString = String.join(",", strings);

        resultsString = resultsString.replace(",", "<br><br>");
        resultsString = resultsString.replace(";", "<br>");

        model.addAttribute("results", resultsString);
        return "getSearchResults";
    }
}
