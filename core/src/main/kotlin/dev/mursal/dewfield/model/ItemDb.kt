package dev.mursal.dewfield.model

/**
 * All balance data in one place: items, crops, animals, machines, recipes, upgrades and levels.
 * Tune the game here. Every id is also an i18n key suffix (`item.<id>`, `animal.<id>`, ...).
 */
enum class Category { SEED, SUPPLY, FEED, CROP, ANIMAL_PRODUCT, ARTISAN, MEAL }

data class ItemDef(
    val id: String,
    val category: Category,
    /** Shop price, or 0 when no shop sells it. */
    val buyPrice: Int = 0,
    /** Base selling price at normal quality, or 0 when it cannot be sold. */
    val sellPrice: Int = 0,
    /** Energy restored when eaten, or 0 when it is not edible. */
    val energy: Int = 0,
    /** Crops, animal products and artisan goods come in normal, silver and gold quality. */
    val hasQuality: Boolean = false,
    /** Farm level needed before a shop sells it. */
    val unlockLevel: Int = 1,
)

data class CropDef(
    val id: String,
    val seed: String,
    val produce: String,
    /** Watered nights needed to ripen. */
    val growDays: Int,
    /** Nights until it is ripe again after a harvest (0 = the plant is used up). */
    val regrowDays: Int = 0,
    val yieldMin: Int = 1,
    val yieldMax: Int = 1,
    /** Dry nights in a row before the plant withers. Expensive crops are more fragile. */
    val witherDays: Int,
    val xp: Int,
)

data class AnimalDef(
    val id: String,
    val price: Int,
    val housing: Housing,
    /** Feed item this animal eats (each animal type has its own). */
    val feed: String,
    val feedPerDay: Int,
    val product: String,
    /** Fed days needed per product. */
    val productEvery: Int,
    val unlockLevel: Int,
)

enum class Housing { COOP, BARN }

/** A trough holds one kind of feed. Animals eat from it overnight. */
data class TroughDef(val id: String, val feed: String, val capacity: Int, val housing: Housing)

data class MachineDef(val id: String, val price: Int, val unlockLevel: Int, val slots: Int)

data class RecipeDef(
    val id: String,
    val machine: String,
    val input: String,
    val output: String,
    val outputQty: Int,
    /** Artisan goods keep the quality of what went in. */
    val keepsQuality: Boolean,
)

data class MealDef(val id: String, val ingredients: List<String>)

data class UpgradeLevel(val price: Int, val unlockLevel: Int, val value: Int)

data class UpgradeDef(val id: String, val levels: List<UpgradeLevel>) {
    val maxLevel: Int get() = levels.size
}

enum class Weather { SUNNY, CLOUDY, RAIN }

/** What the farmer can wish for at the market fountain. */
enum class Wish { RAIN, GIFT, ENERGY }

/** Where a keepsake turns up: sparkles on the ground, a lucky clover harvest, the fountain or a shooting star. */
enum class KeepsakeSource { GROUND, CLOVER, FOUNTAIN, STAR }

/** A small found object for the shelf at home. It has no price and never enters the bag. */
data class KeepsakeDef(val id: String, val source: KeepsakeSource)

object ItemDb {
    const val FERTILIZER = "fertilizer"

    val items: Map<String, ItemDef> = listOf(
        // Seeds, cheap and quick to expensive and slow (prices are per seed).
        ItemDef("radish_seeds", Category.SEED, buyPrice = 10),
        ItemDef("lettuce_seeds", Category.SEED, buyPrice = 18),
        ItemDef("wheat_seeds", Category.SEED, buyPrice = 8),
        ItemDef("clover_seeds", Category.SEED, buyPrice = 6),
        ItemDef("carrot_seeds", Category.SEED, buyPrice = 32),
        ItemDef("potato_seeds", Category.SEED, buyPrice = 40, unlockLevel = 2),
        ItemDef("tomato_seeds", Category.SEED, buyPrice = 65, unlockLevel = 2),
        ItemDef("corn_seeds", Category.SEED, buyPrice = 55, unlockLevel = 3),
        ItemDef("strawberry_seeds", Category.SEED, buyPrice = 110, unlockLevel = 4),
        ItemDef("pumpkin_seeds", Category.SEED, buyPrice = 160, unlockLevel = 5),
        ItemDef(FERTILIZER, Category.SUPPLY, buyPrice = 20),

        // Feed: every animal eats something different.
        ItemDef("grain", Category.FEED, buyPrice = 6),
        ItemDef("hay", Category.FEED, buyPrice = 5, unlockLevel = 2),
        ItemDef("clover_mix", Category.FEED, buyPrice = 7, unlockLevel = 3),

        // Crops.
        ItemDef("radish", Category.CROP, sellPrice = 24, energy = 8, hasQuality = true),
        ItemDef("lettuce", Category.CROP, sellPrice = 42, energy = 10, hasQuality = true),
        ItemDef("wheat", Category.CROP, sellPrice = 12, hasQuality = true),
        ItemDef("clover", Category.CROP, sellPrice = 8, hasQuality = true),
        ItemDef("carrot", Category.CROP, sellPrice = 80, energy = 12, hasQuality = true),
        ItemDef("potato", Category.CROP, sellPrice = 38, hasQuality = true),
        ItemDef("tomato", Category.CROP, sellPrice = 30, energy = 8, hasQuality = true),
        ItemDef("corn", Category.CROP, sellPrice = 48, energy = 8, hasQuality = true),
        ItemDef("strawberry", Category.CROP, sellPrice = 28, energy = 10, hasQuality = true),
        ItemDef("pumpkin", Category.CROP, sellPrice = 450, hasQuality = true),

        // Animal products and artisan goods.
        ItemDef("egg", Category.ANIMAL_PRODUCT, sellPrice = 30, hasQuality = true),
        ItemDef("milk", Category.ANIMAL_PRODUCT, sellPrice = 75, energy = 15, hasQuality = true),
        ItemDef("wool", Category.ANIMAL_PRODUCT, sellPrice = 140, hasQuality = true),
        ItemDef("cheese", Category.ARTISAN, sellPrice = 170, energy = 35, hasQuality = true),
        ItemDef("cloth", Category.ARTISAN, sellPrice = 320, hasQuality = true),

        // Meals cooked on the stove. They restore lots of energy and cannot be sold.
        ItemDef("fried_egg", Category.MEAL, energy = 40),
        ItemDef("salad", Category.MEAL, energy = 50),
        ItemDef("veggie_stew", Category.MEAL, energy = 60),
        ItemDef("pancakes", Category.MEAL, energy = 75),
        ItemDef("pumpkin_soup", Category.MEAL, energy = 100),
    ).associateBy { it.id }

    val crops: Map<String, CropDef> = listOf(
        CropDef("radish", "radish_seeds", "radish", growDays = 3, witherDays = 4, xp = 2),
        CropDef("lettuce", "lettuce_seeds", "lettuce", growDays = 4, witherDays = 4, xp = 3),
        CropDef("wheat", "wheat_seeds", "wheat", growDays = 4, yieldMin = 2, yieldMax = 2, witherDays = 4, xp = 1),
        CropDef("clover", "clover_seeds", "clover", growDays = 3, regrowDays = 2, yieldMin = 2, yieldMax = 2, witherDays = 4, xp = 1),
        CropDef("carrot", "carrot_seeds", "carrot", growDays = 5, witherDays = 3, xp = 4),
        CropDef("potato", "potato_seeds", "potato", growDays = 6, yieldMin = 2, yieldMax = 3, witherDays = 3, xp = 5),
        CropDef("tomato", "tomato_seeds", "tomato", growDays = 7, regrowDays = 3, yieldMin = 2, yieldMax = 2, witherDays = 3, xp = 3),
        CropDef("corn", "corn_seeds", "corn", growDays = 8, regrowDays = 4, witherDays = 3, xp = 4),
        CropDef("strawberry", "strawberry_seeds", "strawberry", growDays = 8, regrowDays = 3, yieldMin = 3, yieldMax = 3, witherDays = 2, xp = 4),
        CropDef("pumpkin", "pumpkin_seeds", "pumpkin", growDays = 11, witherDays = 2, xp = 18),
    ).associateBy { it.id }

    /** Seed id -> crop. */
    val cropBySeed: Map<String, CropDef> = crops.values.associateBy { it.seed }

    val animals: Map<String, AnimalDef> = listOf(
        AnimalDef("chicken", price = 350, housing = Housing.COOP, feed = "grain", feedPerDay = 1, product = "egg", productEvery = 1, unlockLevel = 1),
        AnimalDef("cow", price = 1400, housing = Housing.BARN, feed = "hay", feedPerDay = 2, product = "milk", productEvery = 1, unlockLevel = 2),
        AnimalDef("sheep", price = 1000, housing = Housing.BARN, feed = "clover", feedPerDay = 1, product = "wool", productEvery = 2, unlockLevel = 3),
    ).associateBy { it.id }

    /** Sheep eat clover. Both the crop and the bought Clover Mix count as clover feed. */
    val feedAliases: Map<String, List<String>> = mapOf(
        "grain" to listOf("grain"),
        "hay" to listOf("hay"),
        "clover" to listOf("clover", "clover_mix"),
    )

    val troughs: Map<String, TroughDef> = listOf(
        TroughDef("hopper", feed = "grain", capacity = 30, housing = Housing.COOP),
        TroughDef("hay_rack", feed = "hay", capacity = 40, housing = Housing.BARN),
        TroughDef("clover_tray", feed = "clover", capacity = 30, housing = Housing.BARN),
    ).associateBy { it.id }

    val troughByFeed: Map<String, TroughDef> = troughs.values.associateBy { it.feed }

    val machines: Map<String, MachineDef> = listOf(
        MachineDef("mill", price = 0, unlockLevel = 1, slots = 10),
        MachineDef("cheese_press", price = 1000, unlockLevel = 3, slots = 5),
        MachineDef("loom", price = 1400, unlockLevel = 4, slots = 3),
    ).associateBy { it.id }

    val recipes: Map<String, RecipeDef> = listOf(
        RecipeDef("mill_wheat", "mill", input = "wheat", output = "hay", outputQty = 5, keepsQuality = false),
        RecipeDef("mill_corn", "mill", input = "corn", output = "grain", outputQty = 10, keepsQuality = false),
        RecipeDef("press_milk", "cheese_press", input = "milk", output = "cheese", outputQty = 1, keepsQuality = true),
        RecipeDef("loom_wool", "loom", input = "wool", output = "cloth", outputQty = 1, keepsQuality = true),
    ).associateBy { it.id }

    val meals: Map<String, MealDef> = listOf(
        MealDef("fried_egg", listOf("egg")),
        MealDef("salad", listOf("lettuce", "tomato")),
        MealDef("veggie_stew", listOf("potato", "carrot")),
        MealDef("pancakes", listOf("egg", "milk", "wheat")),
        MealDef("pumpkin_soup", listOf("pumpkin", "milk")),
    ).associateBy { it.id }

    const val UPGRADE_CAN = "can"
    const val UPGRADE_FIELD = "field"
    const val UPGRADE_COOP = "coop"
    const val UPGRADE_BARN = "barn"

    /** Level 1 of every upgrade is what you start with. */
    val upgrades: Map<String, UpgradeDef> = listOf(
        UpgradeDef(UPGRADE_CAN, listOf(UpgradeLevel(0, 1, 20), UpgradeLevel(600, 2, 40), UpgradeLevel(1800, 4, 80))),
        UpgradeDef(UPGRADE_FIELD, listOf(UpgradeLevel(0, 1, 1), UpgradeLevel(700, 2, 2), UpgradeLevel(1800, 3, 3), UpgradeLevel(3500, 5, 4))),
        UpgradeDef(UPGRADE_COOP, listOf(UpgradeLevel(0, 1, 4), UpgradeLevel(1500, 3, 8))),
        UpgradeDef(UPGRADE_BARN, listOf(UpgradeLevel(0, 1, 3), UpgradeLevel(2600, 4, 6))),
    ).associateBy { it.id }

    /** Total XP needed to reach level index+1. */
    val levelXp = listOf(0, 60, 180, 400, 750, 1250, 2000, 3000, 4500, 6500)

    /** Price multipliers for normal, silver and gold quality. */
    val qualityMultiplier = floatArrayOf(1f, 1.25f, 1.5f)

    val shopSeeds: List<String> = listOf(
        "radish_seeds", "lettuce_seeds", "wheat_seeds", "clover_seeds", "carrot_seeds",
        "potato_seeds", "tomato_seeds", "corn_seeds", "strawberry_seeds", "pumpkin_seeds", FERTILIZER,
    )
    val shopFeed: List<String> = listOf("grain", "hay", "clover_mix")

    /** Everything Leyla buys. */
    val sellable: List<String> = items.values.filter { it.sellPrice > 0 }.map { it.id }

    /** People who post delivery orders on the board. */
    val orderClients = listOf("gulnar", "ramin", "sevda", "elvin", "kamal", "aysel")

    // ------------------------------------------------------------------ pastimes
    // The fountain, benches, keepsakes, shooting stars and the daily gift. Their rewards are small and
    // capped per day, so they add surprises without becoming a better income than farming.

    /** One coin toss at the fountain. */
    const val WISH_COST = 10
    const val WISHES_PER_DAY = 3

    /** Chance that a wish comes true. The first wish on a farm always does. */
    val wishChance: Map<Wish, Float> = mapOf(Wish.RAIN to 0.35f, Wish.GIFT to 0.3f, Wish.ENERGY to 0.45f)
    const val WISH_ENERGY = 20

    /** Bench rest: one energy every [REST_MINUTES] game minutes (twice as fast at sunset), up to [REST_CAP] a day. */
    const val REST_MINUTES = 4.0
    const val REST_CAP = 20
    const val SUNSET_START = 18 * 60
    const val SUNSET_END = 20 * 60 + 30

    /**
     * A small gift (a granted wish, a shooting star): a pair of cheap seeds, some fertilizer or a little
     * feed. Only seeds up to [GIFT_SEED_MAX_PRICE] qualify, so luck never pays for an expensive field.
     */
    const val GIFT_SEEDS = 2
    const val GIFT_SEED_MAX_PRICE = 40
    const val GIFT_FERTILIZER = 2
    const val GIFT_FEED = 5

    /** Coins at the end of the rainbow that shows the morning after rain. */
    const val RAINBOW_COINS_MIN = 15
    const val RAINBOW_COINS_MAX = 35

    /** Share of days with a sparkle somewhere on the ground. */
    const val SPARKLE_CHANCE = 0.6f

    /** Once every ground keepsake is found, a sparkle holds a few coins instead. */
    const val SPARKLE_COINS_MIN = 5
    const val SPARKLE_COINS_MAX = 15

    /** Chance that a clover harvest or a wish turns up its keepsake, until it is found. */
    const val CLOVER_KEEPSAKE_CHANCE = 0.05f
    const val FOUNTAIN_KEEPSAKE_CHANCE = 0.08f
    const val KEEPSAKE_XP = 10

    /** One-time reward for a full shelf. */
    const val SHELF_REWARD_COINS = 250
    const val SHELF_REWARD_XP = 40

    val keepsakes: List<KeepsakeDef> = listOf(
        KeepsakeDef("horseshoe", KeepsakeSource.GROUND),
        KeepsakeDef("marble", KeepsakeSource.GROUND),
        KeepsakeDef("pocket_watch", KeepsakeSource.GROUND),
        KeepsakeDef("shell", KeepsakeSource.GROUND),
        KeepsakeDef("arrowhead", KeepsakeSource.GROUND),
        KeepsakeDef("fossil", KeepsakeSource.GROUND),
        KeepsakeDef("four_leaf", KeepsakeSource.CLOVER),
        KeepsakeDef("wish_coin", KeepsakeSource.FOUNTAIN),
        KeepsakeDef("star_shard", KeepsakeSource.STAR),
    )

    fun keepsakesFrom(source: KeepsakeSource): List<String> = keepsakes.filter { it.source == source }.map { it.id }

    /**
     * The daily gift calendar, one entry per day the player visits, round and round. Missing a day
     * never resets it. "coins" is money; everything else is an item id.
     */
    val dailyGifts: List<List<Gain>> = listOf(
        listOf(Gain("coins", 25)),
        listOf(Gain(FERTILIZER, 2)),
        listOf(Gain("lettuce_seeds", 4)),
        listOf(Gain("grain", 10)),
        listOf(Gain("coins", 50)),
        listOf(Gain(FERTILIZER, 3)),
        listOf(Gain("coins", 100), Gain("pancakes", 1)),
    )

    fun item(id: String): ItemDef = items[id] ?: error("Unknown item $id")
    fun crop(id: String): CropDef = crops[id] ?: error("Unknown crop $id")
    fun animal(id: String): AnimalDef = animals[id] ?: error("Unknown animal $id")
    fun machine(id: String): MachineDef = machines[id] ?: error("Unknown machine $id")
    fun recipe(id: String): RecipeDef = recipes[id] ?: error("Unknown recipe $id")
    fun upgrade(id: String): UpgradeDef = upgrades[id] ?: error("Unknown upgrade $id")
    fun recipesFor(machine: String): List<RecipeDef> = recipes.values.filter { it.machine == machine }

    /** The level at which something becomes available, for shop locks. */
    fun unlockLevelOf(id: String): Int = items[id]?.unlockLevel ?: animals[id]?.unlockLevel ?: machines[id]?.unlockLevel ?: 1
}
