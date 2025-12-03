import java.util.HashSet;
import java.util.Set;

public class RandomNameTester {
    public static void main(String[] args) {
        System.out.println("=== Random Name Generation Testing ===");
        
        // This simulates the Defold random name generation logic
        String[] adjectives = {"Swift", "Clever", "Bold", "Quick", "Smart", "Brave", "Cool", "Sharp", "Epic", "Pro", "Elite", "Super", "Mega", "Ultra", "Alpha", "Prime", "Frost", "Fire", "Storm", "Thunder", "Shadow", "Light", "Dark", "Golden", "Silver", "Crimson", "Azure", "Emerald", "Ruby", "Diamond"};
        String[] nouns = {"Gamer", "Player", "Hero", "Champion", "Master", "Ace", "Ninja", "Tiger", "Eagle", "Wolf", "Phoenix", "Dragon", "Knight", "Warrior", "Legend", "Hunter", "Scout", "Ranger", "Paladin", "Wizard", "Rogue", "Archer", "Mage", "Berserker", "Guardian", "Striker", "Defender", "Assassin", "Gladiator"};
        
        System.out.println("Test 1: Uniqueness Test - Generate 1000 names");
        Set<String> generatedNames = new HashSet<>();
        int duplicates = 0;
        
        for (int i = 0; i < 1000; i++) {
            // Simulate different seeds to test uniqueness
            java.util.Random rand = new java.util.Random(System.nanoTime() + i * 7 + i * 13);
            
            String adj = adjectives[rand.nextInt(adjectives.length)];
            String noun = nouns[rand.nextInt(nouns.length)];
            int number = rand.nextInt(9990) + 10; // 10-9999
            
            String name = adj + noun + number;
            
            if (generatedNames.contains(name)) {
                duplicates++;
                System.out.println("   Duplicate found: " + name);
            } else {
                generatedNames.add(name);
            }
        }
        
        System.out.println("   Total unique names: " + generatedNames.size());
        System.out.println("   Duplicates: " + duplicates);
        System.out.println("   Uniqueness rate: " + (generatedNames.size() / 10.0) + "%");
        
        System.out.println("\nTest 2: Sample Names Generated:");
        generatedNames.stream().limit(10).forEach(name -> System.out.println("   " + name));
        
        System.out.println("\nTest 3: Theoretical Maximum Combinations:");
        long maxCombinations = (long) adjectives.length * nouns.length * 9990;
        System.out.println("   Possible combinations: " + maxCombinations);
        
        System.out.println("\n=== Random Name Testing Complete ===");
    }
}