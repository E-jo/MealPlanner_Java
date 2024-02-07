package mealplanner;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class Main {
    // category, meal name, ingredients
    static Map<String, Map<String, List<String>>> meals = new LinkedHashMap<>();
    static Scanner scanner = new Scanner(System.in);
    static String[] days = new String[] {"Monday", "Tuesday", "Wednesday",
                            "Thursday", "Friday", "Saturday", "Sunday"};
    static final String DB_URL = "jdbc:postgresql:meals_db";
    static final String USER = "postgres";
    static final String PASSWORD = "1111";
    static Connection connection;
    static int mealIdCounter;
    static int ingredientIdCounter;

    public static void main(String[] args) throws SQLException {
        connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
        connection.setAutoCommit(true);

        Statement statement = connection.createStatement();

        statement.executeUpdate("create table if not exists meals (" +
                "meal_id integer primary key," +
                "category varchar(1024)," +
                "meal varchar(1024)" +
                ")");
        statement.executeUpdate("create table if not exists ingredients (" +
                "ingredient_id integer primary key," +
                "meal_id integer," +
                "ingredient varchar(1024)," +
                "foreign key (meal_id) references meals(meal_id)" +
                ")");
        statement.executeUpdate("create table if not exists plan (" +
                "day varchar(32)," +
                "meal_id integer," +
                "category varchar(1024)," +
                "meal varchar(1024)," +
                "foreign key (meal_id) references meals(meal_id)" +
                ")");

        meals = loadMealsDb();
        resetCounters();

        boolean running = true;
        while (running) {
            System.out.println("What would you like to do (add, show, plan, save, exit)?");
            String command = scanner.nextLine().toLowerCase();
            switch (command) {
                case "add" -> addMeal();
                case "show" -> showMeals();
                case "plan" -> planMeals();
                case "save" -> saveShoppingList();
                case "exit" -> running = false;
            }
        }

        System.out.println("Bye!");
        statement.close();
        connection.close();
        scanner.close();
    }

    private static boolean isMealsPlanned() throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM plan");
        ResultSet resultSet = statement.executeQuery();
        resultSet.next();
        int rowCount = resultSet.getInt(1);
        statement.close();
        System.err.println(rowCount);
        return rowCount > 0;
    }

    private static void saveShoppingList() throws SQLException {
        if (isMealsPlanned()) {
            System.out.println("Input a filename:");
            String fileName = scanner.nextLine().trim();
            try (FileWriter writer = new FileWriter(fileName)) {
                for (String ingredient : generateShoppingList()) {
                    writer.write(ingredient + "\n");
                }
                System.out.println("Saved!");
            } catch (IOException e) {
                System.out.println("Failed to save the shopping list: " + e.getMessage());
            }
        } else {
            System.out.println("Unable to save. Plan your meals first.");
        }
    }

    private static List<String> generateShoppingList() throws SQLException {
        Map<String, Integer> shoppingListMap = new HashMap<>();
        try {
            for (String day : days) {
                updateShoppingListMap(shoppingListMap, getIngredientsForDay(day, "breakfast"));
                updateShoppingListMap(shoppingListMap, getIngredientsForDay(day, "lunch"));
                updateShoppingListMap(shoppingListMap, getIngredientsForDay(day, "dinner"));
            }
        } catch (SQLException e) {
            System.out.println("Failed to generate shopping list: " + e.getMessage());
        }
        return formatShoppingList(shoppingListMap);
    }

    private static void updateShoppingListMap(Map<String, Integer> shoppingListMap, List<String> ingredients) {
        for (String ingredient : ingredients) {
            shoppingListMap.put(ingredient, shoppingListMap.getOrDefault(ingredient, 0) + 1);
        }
    }

    private static List<String> getIngredientsForDay(String day, String category) throws SQLException {
        List<String> ingredients = new ArrayList<>();
        PreparedStatement statement = connection.prepareStatement(
                "SELECT ingredients.ingredient " +
                        "FROM plan " +
                        "JOIN meals ON plan.meal_id = meals.meal_id " +
                        "JOIN ingredients ON meals.meal_id = ingredients.meal_id " +
                        "WHERE plan.day = ? AND plan.category = ?");
        statement.setString(1, day);
        statement.setString(2, category);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
            ingredients.add(resultSet.getString("ingredient"));
        }
        statement.close();
        return ingredients;
    }

    private static List<String> formatShoppingList(Map<String, Integer> shoppingListMap) {
        List<String> formattedShoppingList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : shoppingListMap.entrySet()) {
            String ingredient = entry.getKey();
            int quantity = entry.getValue();
            if (quantity > 1) {
                formattedShoppingList.add(ingredient + " x" + quantity);
            } else {
                formattedShoppingList.add(ingredient);
            }
        }
        return formattedShoppingList;
    }

    private static void planMeals() throws SQLException {
        meals = loadMealsDb();

        // first delete any previous plan, if it exists
        Statement clearStatement = connection.createStatement();
        clearStatement.executeUpdate("TRUNCATE TABLE plan");
        clearStatement.close();

        for (String day : days) {
            System.out.println(day);

            List<String> breakfastNames = asSortedList(meals.get("breakfast").keySet());
            for (String breakfastName : breakfastNames) {
                System.out.println(breakfastName);
            }

            System.out.println("Choose the breakfast for " + day + " from the list above:");
            String breakfastChoice = selectMealFromList(meals.get("breakfast"));

            List<String> lunchNames = asSortedList(meals.get("lunch").keySet());
            for (String lunchName : lunchNames) {
                System.out.println(lunchName);
            }

            System.out.println("Choose the lunch for " + day + " from the list above:");
            String lunchChoice = selectMealFromList(meals.get("lunch"));

            List<String> dinnerNames = asSortedList(meals.get("dinner").keySet());
            for (String dinnerName : dinnerNames) {
                System.out.println(dinnerName);
            }

            System.out.println("Choose the dinner for " + day + " from the list above:");
            String dinnerChoice = selectMealFromList(meals.get("dinner"));

            // insert selected meals into the plan table
            PreparedStatement planStatement = connection.prepareStatement(
                    "INSERT INTO plan (day, meal_id, category, meal) VALUES (?, ?, ?, ?)");

            // insert breakfast
            int breakfastId = getMealId(breakfastChoice, "breakfast");
            planStatement.setString(1, day);
            planStatement.setInt(2, breakfastId);
            planStatement.setString(3, "breakfast");
            planStatement.setString(4, breakfastChoice);
            planStatement.executeUpdate();

            // insert lunch
            int lunchId = getMealId(lunchChoice, "lunch");
            planStatement.setString(1, day);
            planStatement.setInt(2, lunchId);
            planStatement.setString(3, "lunch");
            planStatement.setString(4, lunchChoice);
            planStatement.executeUpdate();

            // insert dinner
            int dinnerId = getMealId(dinnerChoice, "dinner");
            planStatement.setString(1, day);
            planStatement.setInt(2, dinnerId);
            planStatement.setString(3, "dinner");
            planStatement.setString(4, dinnerChoice);
            planStatement.executeUpdate();

            System.out.println("Yeah! We planned the meals for " + day + ".");
            System.out.println();
        }
        displayMealPlan();
    }

    private static void displayMealPlan() throws SQLException {
        for (String day : days) {
            System.out.println(day);
            System.out.println("Breakfast: " + getMealForDay(day, "breakfast"));
            System.out.println("Lunch: " + getMealForDay(day, "lunch"));
            System.out.println("Dinner: " + getMealForDay(day, "dinner"));
            System.out.println();
        }
    }

    private static String getMealForDay(String day, String category) throws SQLException {
        String meal = null;
        PreparedStatement mealStatement = connection.prepareStatement(
                "SELECT meals.meal " +
                        "FROM plan " +
                        "JOIN meals ON plan.meal_id = meals.meal_id " +
                        "WHERE plan.day = ? AND plan.category = ? AND meals.category = ?");
        mealStatement.setString(1, day);
        mealStatement.setString(2, category);
        mealStatement.setString(3, category);
        ResultSet resultSet = mealStatement.executeQuery();
        if (resultSet.next()) {
            meal = resultSet.getString("meal");
        }
        mealStatement.close();
        return meal != null ? meal : "No meal planned";
    }

    private static String selectMealFromList(Map<String, List<String>> mealMap) {
        while (true) {
            String input = scanner.nextLine();
            if (!mealMap.containsKey(input)) {
                System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
            } else {
                return input;
            }
        }
    }

    private static int getMealId(String mealName, String category) throws SQLException {
        PreparedStatement mealIdStatement = connection.prepareStatement(
                "SELECT meal_id FROM meals WHERE category = ? AND meal = ?");
        mealIdStatement.setString(1, category);
        mealIdStatement.setString(2, mealName);
        ResultSet mealIdResult = mealIdStatement.executeQuery();
        if (mealIdResult.next()) {
            return mealIdResult.getInt("meal_id");
        }
        return -1;
    }

    public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<T>(c);
        java.util.Collections.sort(list);
        return list;
    }

    private static void resetCounters() throws SQLException {
        Statement statement = connection.createStatement();

        ResultSet mealIdResult = statement.executeQuery("SELECT MAX(meal_id) FROM meals");
        if (mealIdResult.next()) {
            mealIdCounter = mealIdResult.getInt(1) + 1;
        } else {
            mealIdCounter = 1;
        }

        ResultSet ingredientIdResult = statement.executeQuery("SELECT MAX(ingredient_id) FROM ingredients");
        if (ingredientIdResult.next()) {
            ingredientIdCounter = ingredientIdResult.getInt(1) + 1;
        } else {
            ingredientIdCounter = 1;
        }

        statement.close();
    }
    private static Map<String, Map<String, List<String>>> loadMealsDb() throws SQLException {
        Map<String, Map<String, List<String>>> loadedMeals = new LinkedHashMap<>();
        Statement statement = connection.createStatement();
        ResultSet mealsResultSet = statement.executeQuery("SELECT * FROM meals");

        while (mealsResultSet.next()) {
            String category = mealsResultSet.getString("category");
            String meal = mealsResultSet.getString("meal");
            int mealId = mealsResultSet.getInt("meal_id");

            PreparedStatement ingredientsStatement = connection.prepareStatement(
                    "SELECT ingredient FROM ingredients WHERE meal_id = ?");
            ingredientsStatement.setInt(1, mealId);
            ResultSet ingredientsResultSet = ingredientsStatement.executeQuery();

            Map<String, List<String>> mealMap = loadedMeals.computeIfAbsent(category, k -> new LinkedHashMap<>());
            List<String> ingredientList = new ArrayList<>();
            while (ingredientsResultSet.next()) {
                String ingredient = ingredientsResultSet.getString("ingredient");
                ingredientList.add(ingredient);
            }
            mealMap.put(meal, ingredientList);

            ingredientsStatement.close();
            ingredientsResultSet.close();
        }

        statement.close();
        mealsResultSet.close();

        return loadedMeals;
    }

    private static void showMeals() throws SQLException {
        meals = loadMealsDb();
        String category;

        while (true) {
            System.out.println("Which category do you want to print (breakfast, lunch, dinner)?");
            category = scanner.nextLine().trim();

            if (category.isEmpty()) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
                continue;
            }

            if (!category.equalsIgnoreCase("breakfast") &&
                    !category.equalsIgnoreCase("lunch") &&
                    !category.equalsIgnoreCase("dinner")) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
                continue;
            }
            break;
        }
        if (meals.isEmpty()) {
            System.out.println("No meals saved. Add a meal first.");
            return;
        }

        if (!meals.containsKey(category)) {
            System.out.println("No meals found.");
            return;
        }
        System.out.println("Category: " + category);
        Map<String, List<String>> mealMap = meals.get(category);
        //System.out.println("Meals found: " + mealMap.size());
        for (String name : mealMap.keySet()) {
            System.out.println("Name: " + name);
            List<String> ingredients = mealMap.get(name);
            System.out.println("Ingredients:");
            for (String ingredient : ingredients) {
                System.out.println(ingredient);
            }
            System.out.println();
        }
    }
    private static void addMeal() {
        String category;
        String name;
        List<String> ingredients;
        System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");

        while (true) {
            category = scanner.nextLine().trim();
            if (category.isEmpty()) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
                continue;
            }
            if (!category.equalsIgnoreCase("breakfast") &&
                    !category.equalsIgnoreCase("lunch") &&
                    !category.equalsIgnoreCase("dinner")) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
            } else {
                break;
            }
        }

        System.out.println("Input the meal's name:");
        while (true) {
            name = scanner.nextLine().trim();
            if (name.isEmpty()) {
                System.out.println("Wrong format. Use letters only!");
                continue;
            }
            if (invalidInput(name)) {
                System.out.println("Wrong format. Use letters only!");
            } else {
                break;
            }
        }

        System.out.println("Input the ingredients:");
        while (true) {
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                System.out.println("Wrong format. Use letters only!");
                continue;
            }
            boolean validIngredients = true;

            if (invalidInput(input)) {
                System.out.println("Wrong format. Use letters only!");
                continue;
            }
            String[] ingredientArray = input.split(",");
            ingredients = new ArrayList<>();
            for (String ingredient : ingredientArray) {
                if (ingredient.isEmpty()) {
                    System.out.println("Wrong format. Use letters only!");
                    validIngredients = false;
                    break;
                }
                ingredient = ingredient.trim();
                ingredients.add(ingredient);
            }

            if (validIngredients) {
                Map<String, List<String>> meal = new LinkedHashMap<>();
                meal.put(name, ingredients);
                meals.put(category, meal);

                try {
                    PreparedStatement mealStatement = connection.prepareStatement(
                            "INSERT INTO meals (meal_id, category, meal) VALUES (?, ?, ?)");
                    mealStatement.setInt(1, mealIdCounter++);
                    mealStatement.setString(2, category);
                    mealStatement.setString(3, name);
                    mealStatement.executeUpdate();
                    mealStatement.close();

                    for (String ingredient : ingredients) {
                        PreparedStatement ingredientStatement = connection.prepareStatement(
                                "INSERT INTO ingredients (ingredient_id, meal_id, ingredient) VALUES (?, ?, ?)");
                        ingredientStatement.setInt(1, ingredientIdCounter++);
                        ingredientStatement.setInt(2, mealIdCounter - 1); // latest meal_id will be minus 1
                        ingredientStatement.setString(3, ingredient.trim());
                        ingredientStatement.executeUpdate();
                        ingredientStatement.close();
                    }

                    System.out.println("The meal has been added!");
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                break;
            }
        }
    }

    private static boolean invalidInput(String input) {
        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                return true;
            }
            if (!part.matches("[a-zA-Z\\s]*")) {
                return true;
            }
        }
        return false;
    }
}