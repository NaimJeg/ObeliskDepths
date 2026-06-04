package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.DamageNexusApi;
import io.github.naimjeg.damagenexus.api.DamageNexusAttributes;
import io.github.naimjeg.damagenexus.api.critical.CriticalDecision;
import io.github.naimjeg.damagenexus.api.damage.DamageRequest;
import io.github.naimjeg.damagenexus.api.damage.DamageRequestKind;
import io.github.naimjeg.damagenexus.api.damage.DamageResult;
import io.github.naimjeg.damagenexus.api.damage.DamageSettlementSnapshot;
import io.github.naimjeg.damagenexus.api.damage.DamageSourceDescriptor;
import io.github.naimjeg.damagenexus.api.enums.DamageChannel;
import io.github.naimjeg.damagenexus.api.event.DamageNexusRegisterEvent;
import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.damagenexus.api.item.template.DamageAffixTemplateReference;
import io.github.naimjeg.damagenexus.api.item.template.DamageEntryTemplateReference;
import io.github.naimjeg.damagenexus.api.item.template.DamageItemTemplateReferences;
import io.github.naimjeg.damagenexus.api.item.template.DamageNexusTemplates;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardDelivery;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.RewardDeliveryPlan;
import io.github.naimjeg.obeliskdepths.dungeon.reward.UniqueEquipmentRewardItemSource;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingRoller;
import io.github.naimjeg.obeliskdepths.tempering.ResolvedTemperingState;
import io.github.naimjeg.obeliskdepths.tempering.TemperingResolver;
import io.github.naimjeg.obeliskdepths.tempering.TemperingResult;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateData;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/** Launch-only public-API integration coverage for Obelisk equipment. */
public final class ObeliskEquipmentGameTests {
    private static final float EPSILON = 0.001F;
    private static final ThreadLocal<LivingEntity> FORCE_CRITICAL_TARGET =
            new ThreadLocal<>();

    private static final ResourceKey<Consumer<GameTestHelper>>
            PIPELINE_FUNCTION = functionKey("equipment_pipeline");
    private static final ResourceKey<Consumer<GameTestHelper>>
            CONDITION_FUNCTION = functionKey("equipment_conditions");
    private static final ResourceKey<Consumer<GameTestHelper>>
            ARMOR_FUNCTION = functionKey("equipment_armor_transaction");
    private static final ResourceKey<Consumer<GameTestHelper>>
            PERSISTENCE_FUNCTION = functionKey("equipment_persistence_reward");
    private static final ResourceKey<Consumer<GameTestHelper>>
            UNIQUE_CRITICAL_FUNCTION = functionKey("unique_critical_health");
    private static final ResourceKey<Consumer<GameTestHelper>>
            UNIQUE_DEFENSE_FUNCTION = functionKey("unique_defense");
    private static final ResourceKey<Consumer<GameTestHelper>>
            UNIQUE_EFFECTS_FUNCTION = functionKey("unique_effect_conditions");
    private static final ResourceKey<Consumer<GameTestHelper>>
            UNIQUE_STACK_FUNCTION = functionKey("unique_stack_reward");

    private ObeliskEquipmentGameTests() {
    }

    public static void registerDamageNexusExtensions(
            DamageNexusRegisterEvent event
    ) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        event.registerCriticalDecisionProvider(
                id("gametest/force_critical"),
                1000,
                (context, collector) -> {
                    if (context.victim() == FORCE_CRITICAL_TARGET.get()) {
                        collector.contribute(CriticalDecision.FORCE_CRITICAL);
                    }
                }
        );
    }

    public static void registerTestFunctions(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        registerFunction(event, PIPELINE_FUNCTION,
                ObeliskEquipmentGameTests::equipmentPipeline);
        registerFunction(event, CONDITION_FUNCTION,
                ObeliskEquipmentGameTests::equipmentConditions);
        registerFunction(event, ARMOR_FUNCTION,
                ObeliskEquipmentGameTests::equipmentArmorTransaction);
        registerFunction(event, PERSISTENCE_FUNCTION,
                ObeliskEquipmentGameTests::equipmentPersistenceReward);
        registerFunction(event, UNIQUE_CRITICAL_FUNCTION,
                ObeliskEquipmentGameTests::uniqueCriticalAndHealth);
        registerFunction(event, UNIQUE_DEFENSE_FUNCTION,
                ObeliskEquipmentGameTests::uniqueDefense);
        registerFunction(event, UNIQUE_EFFECTS_FUNCTION,
                ObeliskEquipmentGameTests::uniqueEffectConditions);
        registerFunction(event, UNIQUE_STACK_FUNCTION,
                ObeliskEquipmentGameTests::uniqueStackAndReward);
    }

    public static void registerGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(
                        id("equipment_environment"),
                        new TestEnvironmentDefinition.AllOf(List.of())
                );
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                environment,
                Identifier.withDefaultNamespace("empty"),
                100,
                0,
                true,
                Rotation.NONE
        );
        registerTest(event, PIPELINE_FUNCTION, data);
        registerTest(event, CONDITION_FUNCTION, data);
        registerTest(event, ARMOR_FUNCTION, data);
        registerTest(event, PERSISTENCE_FUNCTION, data);
        registerTest(event, UNIQUE_CRITICAL_FUNCTION, data);
        registerTest(event, UNIQUE_DEFENSE_FUNCTION, data);
        registerTest(event, UNIQUE_EFFECTS_FUNCTION, data);
        registerTest(event, UNIQUE_STACK_FUNCTION, data);
    }

    private static void equipmentPipeline(GameTestHelper helper) {
        ArmorStand attacker = attacker(helper);

        DamageSettlementSnapshot tempered = submit(
                helper, attacker, target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                ObeliskEquipmentIds.TEMPERED, false
        );
        assertChannel(tempered, DamageChannel.PHYSICAL_ID, 13.0F,
                "tempered physical channel");
        assertClose(tempered.resolvedDamage(), 13.0F,
                "tempered resolved damage");

        DamageSettlementSnapshot converted = submit(
                helper, attacker, target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                ObeliskEquipmentIds.FLAMEFORGED, false
        );
        assertChannel(converted, DamageChannel.PHYSICAL_ID, 8.0F,
                "flameforged physical remainder");
        assertChannel(converted, DamageChannel.FIRE_ID, 2.0F,
                "flameforged fire conversion");
        assertClose(converted.resolvedDamage(), 10.0F,
                "flameforged resolved damage");

        DamageSettlementSnapshot extra = submit(
                helper, attacker, target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                ObeliskEquipmentIds.SPELLBLADE, false
        );
        assertChannel(extra, DamageChannel.PHYSICAL_ID, 10.0F,
                "spellblade retained physical");
        assertChannel(extra, DamageChannel.MAGIC_ID, 1.5F,
                "spellblade additional magic");
        assertClose(extra.resolvedDamage(), 11.5F,
                "spellblade resolved damage");

        ItemStack armor = new ItemStack(ModItems.EXILE_CHESTPLATE.get());
        addReference(armor, ObeliskEquipmentIds.TEMPERED);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        attacker.setItemSlot(EquipmentSlot.CHEST, armor);
        DamageSettlementSnapshot armorEntry = submitEquipped(
                helper,
                attacker,
                target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                false
        );
        assertChannel(armorEntry, DamageChannel.PHYSICAL_ID, 13.0F,
                "generic armor entry");

        helper.succeed();
    }

    private static void equipmentConditions(GameTestHelper helper) {
        ArmorStand attacker = attacker(helper);

        DamageSettlementSnapshot nonCritical = submit(
                helper, attacker, target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                ObeliskEquipmentIds.DEADLY, false
        );
        assertClose(nonCritical.resolvedDamage(), 10.0F,
                "deadly non-critical damage");

        DamageSettlementSnapshot criticalControl = submit(
                helper, attacker, target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                null, true
        );
        DamageSettlementSnapshot criticalDeadly = submit(
                helper, attacker, target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                ObeliskEquipmentIds.DEADLY, true
        );
        if (!criticalControl.critical() || !criticalDeadly.critical()) {
            throw new AssertionError("critical provider did not freeze true");
        }
        assertClose(
                criticalDeadly.resolvedDamage()
                        - criticalControl.resolvedDamage(),
                2.0F,
                "deadly final-critical increment"
        );

        ItemStack duplicateCriticalGroup = new ItemStack(Items.IRON_SWORD);
        DamageNexusItemApi.setTemplateReferences(
                duplicateCriticalGroup,
                new DamageItemTemplateReferences(
                        List.of(
                                new DamageEntryTemplateReference(
                                        ObeliskEquipmentIds.DEADLY),
                                new DamageEntryTemplateReference(
                                        ObeliskEquipmentIds.CRITICAL_EDGE)
                        ),
                        List.of()
                )
        );
        attacker.setItemSlot(EquipmentSlot.MAINHAND, duplicateCriticalGroup);
        attacker.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        DamageSettlementSnapshot deduplicated = submitEquipped(
                helper,
                attacker,
                target(helper, EntityType.ZOMBIE, 1.0F, 0.0),
                true
        );
        assertClose(deduplicated.resolvedDamage(),
                criticalDeadly.resolvedDamage(),
                "critical stacking group");

        assertConditional(
                helper, attacker, ObeliskEquipmentIds.AMBUSHERS,
                target(helper, EntityType.ZOMBIE, 0.90F, 0.0), 11.8F,
                target(helper, EntityType.ZOMBIE, 0.80F, 0.0), 10.0F,
                "ambushers"
        );
        assertConditional(
                helper, attacker, ObeliskEquipmentIds.EXECUTIONERS,
                target(helper, EntityType.ZOMBIE, 0.30F, 0.0), 12.0F,
                target(helper, EntityType.ZOMBIE, 0.35F, 0.0), 10.0F,
                "executioners"
        );

        WitherBoss boss = target(helper, EntityType.WITHER, 1.0F, 0.0);
        boss.setInvulnerableTicks(0);
        assertConditional(
                helper, attacker, ObeliskEquipmentIds.GIANT_SLAYERS,
                boss, 12.0F,
                target(helper, EntityType.ZOMBIE, 1.0F, 0.0), 10.0F,
                "giant_slayers"
        );

        Monster burning = target(helper, EntityType.ZOMBIE, 1.0F, 0.0);
        burning.igniteForSeconds(10.0F);
        assertConditional(
                helper, attacker, ObeliskEquipmentIds.SMOLDERING,
                burning, 11.5F,
                target(helper, EntityType.ZOMBIE, 1.0F, 0.0), 10.0F,
                "smoldering"
        );

        helper.succeed();
    }

    private static void equipmentArmorTransaction(GameTestHelper helper) {
        ArmorStand attacker = attacker(helper);

        Monster controlTarget = target(
                helper, EntityType.ZOMBIE, 1.0F, 20.0);
        DamageSettlementSnapshot control = submit(
                helper, attacker, controlTarget, null, false);

        Monster sunderedTarget = target(
                helper, EntityType.ZOMBIE, 1.0F, 20.0);
        AttributeInstance armor = sunderedTarget.getAttribute(Attributes.ARMOR);
        DamageSettlementSnapshot sundered = submit(
                helper, attacker, sunderedTarget,
                ObeliskEquipmentIds.SUNDERING, false);
        if (sundered.resolvedDamage() <= control.resolvedDamage()) {
            throw new AssertionError(
                    "sundering did not reduce current-transaction armor effectiveness"
            );
        }
        assertClose((float) armor.getBaseValue(), 20.0F,
                "sundering target armor attribute");

        DamageSettlementSnapshot after = submit(
                helper, attacker,
                target(helper, EntityType.ZOMBIE, 1.0F, 20.0),
                null, false
        );
        assertClose(after.resolvedDamage(), control.resolvedDamage(),
                "sundering transaction isolation");

        helper.succeed();
    }

    private static void equipmentPersistenceReward(GameTestHelper helper) {
        if (ObeliskEquipmentRules.slot(new ItemStack(Items.IRON_SWORD))
                .orElseThrow() != ObeliskEquipmentSlot.WEAPON) {
            throw new AssertionError("tagged weapon slot was not recognized");
        }
        if (ObeliskEquipmentRules.slot(
                new ItemStack(ModItems.EXILE_HELMET.get()))
                .orElseThrow() != ObeliskEquipmentSlot.ARMOR_HEAD) {
            throw new AssertionError("Exile helmet tag/slot was not recognized");
        }
        if (ObeliskEquipmentRules.accepts(new ItemStack(Items.STICK))) {
            throw new AssertionError("non-tagged item entered equipment rules");
        }

        UUID rewardUuid = UUID.fromString(
                "11111111-2222-3333-4444-555555555555");
        UUID instanceUuid = UUID.fromString(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ItemStack base = new ItemStack(Items.IRON_SWORD);
        base.setDamageValue(17);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Fixed Reward"));
        base.enchant(
                helper.getLevel().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SHARPNESS),
                2
        );

        ItemStack first = ObeliskEquipmentGenerator.generateRewardStack(
                base, 123456789L, rewardUuid, instanceUuid, 0);
        ItemStack second = ObeliskEquipmentGenerator.generateRewardStack(
                base, 123456789L, rewardUuid, instanceUuid, 0);
        assertSameStack(first, second, "deterministic reward equipment");
        if (managedReferences(first).size() != 1) {
            throw new AssertionError("reward equipment did not receive one template");
        }
        ItemStack idempotent = ObeliskEquipmentGenerator.generateRewardStack(
                first, 123456789L, rewardUuid, instanceUuid, 0);
        assertSameStack(first, idempotent, "reward generation idempotence");

        ItemStack ordinary = new ItemStack(Items.DIAMOND);
        assertSameStack(
                ordinary,
                ObeliskEquipmentGenerator.generateRewardStack(
                        ordinary, 1L, rewardUuid, instanceUuid, 0),
                "ordinary reward"
        );

        DamageNexusItemApi.addEntryTemplateReference(
                first,
                new DamageEntryTemplateReference(
                        Identifier.fromNamespaceAndPath(
                                "thirdpartymod", "preserved_entry"))
        );
        ObeliskTemperingRecipeInput input = new ObeliskTemperingRecipeInput(
                first,
                TemperingTemplateItems.createTemplate(1, 0.0F),
                new ItemStack(Items.ECHO_SHARD)
        );
        ResolvedTemperingState resolved = TemperingResolver.resolve(
                helper.getLevel().getServer().getRecipeManager(),
                helper.getLevel(),
                input,
                ObeliskTemperingDirectionRegistry.BALANCE
        );
        if (!resolved.actionable()) {
            throw new AssertionError(
                    "reward equipment was not temperable: "
                            + resolved.failure().reason()
            );
        }

        ObeliskTemperingRoller.TemperingResult fixedA =
                ObeliskTemperingRoller.temper(
                        first,
                        new TemperingTemplateData(1, 0.0F),
                        ObeliskTemperingDirectionRegistry.BALANCE,
                        resolved.matchingRecipes(),
                        RandomSource.create(99887766L)
                );
        ObeliskTemperingRoller.TemperingResult fixedB =
                ObeliskTemperingRoller.temper(
                        first,
                        new TemperingTemplateData(1, 0.0F),
                        ObeliskTemperingDirectionRegistry.BALANCE,
                        resolved.matchingRecipes(),
                        RandomSource.create(99887766L)
                );
        if (!fixedA.success() || !fixedB.success()
                || !fixedA.appliedEntryIds().equals(fixedB.appliedEntryIds())) {
            throw new AssertionError("fixed tempering random was not deterministic");
        }
        assertSameStack(fixedA.result(), fixedB.result(),
                "fixed tempering result");

        TemperingResult transaction = TemperingTransaction.execute(
                helper.getLevel(),
                helper.getLevel().getServer().getRecipeManager(),
                input,
                ObeliskTemperingDirectionRegistry.BALANCE
        );
        if (!transaction.success()) {
            throw new AssertionError(
                    "tempering transaction failed: "
                            + transaction.failure().reason()
            );
        }
        ItemStack crafted = transaction.craftedStack();
        if (managedReferences(crafted).size() != 1) {
            throw new AssertionError("tempering did not atomically replace managed references");
        }
        if (DamageNexusItemApi.getEntryTemplateReferences(crafted).stream()
                .noneMatch(reference -> reference.id().getNamespace()
                        .equals("thirdpartymod"))) {
            throw new AssertionError("tempering removed a foreign template reference");
        }
        if (crafted.getDamageValue() != 17
                || !Component.literal("Fixed Reward").equals(
                crafted.get(DataComponents.CUSTOM_NAME))
                || !first.get(DataComponents.ENCHANTMENTS).equals(
                crafted.get(DataComponents.ENCHANTMENTS))) {
            throw new AssertionError("tempering lost unrelated item components");
        }
        if (input.weapon().isEmpty()
                || DamageNexusItemApi.getEntryTemplateReferences(input.weapon())
                .size() != 2) {
            throw new AssertionError("failed copy-on-write tempering boundary");
        }

        var ops = helper.getLevel().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = ItemStack.CODEC.encodeStart(ops, crafted).getOrThrow();
        ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        assertSameStack(crafted, restored, "item reference serialization");

        RewardDeliveryPlan plan = RewardDeliveryPlan.startAt(List.of(crafted), 7);
        Tag planTag = RewardDeliveryPlan.CODEC
                .encodeStart(ops, plan)
                .getOrThrow();
        RewardDeliveryPlan recovered = RewardDeliveryPlan.CODEC
                .parse(ops, planTag)
                .getOrThrow();
        if (recovered.firstOrdinal() != 7 || recovered.nextOrdinal() != 7) {
            throw new AssertionError("reward plan lost its absolute ordinal range");
        }
        assertSameStack(crafted, recovered.currentStack(),
                "reward plan recovery");

        DungeonRewardId rewardId = new DungeonRewardId(rewardUuid);
        DungeonInstanceId instanceId = new DungeonInstanceId(instanceUuid);
        BlockPos sprayPos = helper.absolutePos(new BlockPos(2, 2, 2));
        if (!DungeonRewardDelivery.spawnRewardStack(
                helper.getLevel(), rewardId, instanceId, Optional.empty(),
                123456789L,
                sprayPos,
                recovered.nextOrdinal(),
                recovered.currentStack()
        )) {
            throw new AssertionError("reward spray failed");
        }
        if (!recovered.advance().complete()) {
            throw new AssertionError("reward plan did not complete from an offset ordinal");
        }
        List<ItemEntity> sprayed = helper.getLevel().getEntities(
                EntityType.ITEM,
                new AABB(sprayPos).inflate(3.0D),
                entity -> entity.isAlive()
        );
        if (sprayed.size() != 1) {
            throw new AssertionError(
                    "reward spray entity count was " + sprayed.size());
        }
        assertSameStack(crafted, sprayed.getFirst().getItem(),
                "reward spray persisted stack");

        helper.succeed();
    }

    private static void uniqueCriticalAndHealth(GameTestHelper helper) {
        ArmorStand attacker = attacker(helper);
        try {
            clearEquipment(attacker);
            attacker.setItemSlot(
                    EquipmentSlot.MAINHAND,
                    new ItemStack(Items.NETHERITE_SWORD)
            );
            DamageSettlementSnapshot criticalControl = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    true
            );

            attacker.setItemSlot(
                    EquipmentSlot.MAINHAND,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.GRANDFATHER
                    )
            );
            DamageSettlementSnapshot criticalUnique = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    true
            );
            assertPhysicalAndTotal(criticalControl, 15.0F,
                    "grandfather critical control");
            assertPhysicalAndTotal(criticalUnique, 20.0F,
                    "grandfather critical result");
            assertClose(
                    criticalUnique.resolvedDamage()
                            - criticalControl.resolvedDamage(),
                    5.0F,
                    "grandfather critical base increment"
            );

            DamageSettlementSnapshot nonCritical = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            assertPhysicalAndTotal(nonCritical, 10.0F,
                    "grandfather non-critical result");

            clearEquipment(attacker);
            attacker.setHealth(attacker.getMaxHealth());
            attacker.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_SWORD));
            attacker.setItemSlot(
                    EquipmentSlot.CHEST,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.TYRAELS_MIGHT
                    )
            );
            DamageSettlementSnapshot fullHealth = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            assertChannel(fullHealth, DamageChannel.PHYSICAL_ID, 10.0F,
                    "tyrael full-health physical channel");
            assertChannel(fullHealth, DamageChannel.MAGIC_ID, 4.0F,
                    "tyrael full-health magic channel");
            assertClose(fullHealth.resolvedDamage(), 14.0F,
                    "tyrael full-health resolved damage");

            attacker.setHealth(attacker.getMaxHealth() * 0.99F);
            DamageSettlementSnapshot boundary = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            assertChannel(boundary, DamageChannel.PHYSICAL_ID, 10.0F,
                    "tyrael 99-percent physical channel");
            assertChannel(boundary, DamageChannel.MAGIC_ID, 0.0F,
                    "tyrael 99-percent magic channel");
            assertClose(boundary.resolvedDamage(), 10.0F,
                    "tyrael 99-percent resolved damage");

            helper.succeed();
        } finally {
            FORCE_CRITICAL_TARGET.remove();
            attacker.removeAllEffects();
            clearEquipment(attacker);
        }
    }

    private static void uniqueDefense(GameTestHelper helper) {
        ArmorStand attacker = attacker(helper);
        try {
            clearEquipment(attacker);
            attacker.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_SWORD));

            Monster harlequinControlTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            harlequinControlTarget.setItemSlot(
                    EquipmentSlot.HEAD,
                    new ItemStack(ModItems.EXILE_HELMET.get())
            );
            DamageSettlementSnapshot harlequinControl = submitEquipped(
                    helper, attacker, harlequinControlTarget, false);

            Monster harlequinTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            harlequinTarget.setItemSlot(
                    EquipmentSlot.HEAD,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.HARLEQUIN_CREST
                    )
            );
            DamageSettlementSnapshot harlequin = submitEquipped(
                    helper, attacker, harlequinTarget, false);
            assertClose(
                    harlequin.resolvedDamage(),
                    harlequinControl.resolvedDamage() * 0.90F,
                    "harlequin resolved damage"
            );
            assertChannel(
                    harlequin,
                    DamageChannel.PHYSICAL_ID,
                    harlequinControl.resolvedChannelDamage()
                            .getOrDefault(DamageChannel.PHYSICAL_ID, 0.0F) * 0.90F,
                    "harlequin physical channel"
            );

            ItemStack arcaneWeapon = new ItemStack(Items.IRON_SWORD);
            addReference(arcaneWeapon, ObeliskEquipmentIds.ARCANE);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, arcaneWeapon);

            Monster tyraelControlTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            tyraelControlTarget.setItemSlot(
                    EquipmentSlot.CHEST,
                    new ItemStack(ModItems.EXILE_CHESTPLATE.get())
            );
            DamageSettlementSnapshot tyraelControl = submitEquipped(
                    helper, attacker, tyraelControlTarget, false);

            Monster tyraelTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            tyraelTarget.setItemSlot(
                    EquipmentSlot.CHEST,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.TYRAELS_MIGHT
                    )
            );
            double[] resistanceBefore = resistanceValues(tyraelTarget);
            DamageSettlementSnapshot resisted = submitEquipped(
                    helper, attacker, tyraelTarget, false);
            if (resisted.resolvedChannelDamage()
                    .getOrDefault(DamageChannel.PHYSICAL_ID, 0.0F)
                    >= tyraelControl.resolvedChannelDamage()
                    .getOrDefault(DamageChannel.PHYSICAL_ID, 0.0F)) {
                throw new AssertionError(
                        "Tyrael's Might did not mitigate physical channel damage");
            }
            if (resisted.resolvedChannelDamage()
                    .getOrDefault(DamageChannel.MAGIC_ID, 0.0F)
                    >= tyraelControl.resolvedChannelDamage()
                    .getOrDefault(DamageChannel.MAGIC_ID, 0.0F)) {
                throw new AssertionError(
                        "Tyrael's Might did not mitigate magic channel damage");
            }
            if (resisted.resolvedDamage() >= tyraelControl.resolvedDamage()) {
                throw new AssertionError(
                        "Tyrael's Might did not reduce resolved damage");
            }
            assertResistanceValues(
                    tyraelTarget,
                    resistanceBefore,
                    "tyrael temporary resistance attributes"
            );

            tyraelTarget.setHealth(tyraelTarget.getMaxHealth());
            tyraelTarget.setItemSlot(
                    EquipmentSlot.CHEST,
                    new ItemStack(ModItems.EXILE_CHESTPLATE.get())
            );
            DamageSettlementSnapshot after = submitEquipped(
                    helper, attacker, tyraelTarget, false);
            assertChannel(
                    after,
                    DamageChannel.PHYSICAL_ID,
                    tyraelControl.resolvedChannelDamage()
                            .getOrDefault(DamageChannel.PHYSICAL_ID, 0.0F),
                    "tyrael physical transaction isolation"
            );
            assertChannel(
                    after,
                    DamageChannel.MAGIC_ID,
                    tyraelControl.resolvedChannelDamage()
                            .getOrDefault(DamageChannel.MAGIC_ID, 0.0F),
                    "tyrael magic transaction isolation"
            );
            assertClose(after.resolvedDamage(), tyraelControl.resolvedDamage(),
                    "tyrael resolved transaction isolation");

            helper.succeed();
        } finally {
            FORCE_CRITICAL_TARGET.remove();
            attacker.removeAllEffects();
            clearEquipment(attacker);
        }
    }

    private static void uniqueEffectConditions(GameTestHelper helper) {
        ArmorStand attacker = attacker(helper);
        try {
            clearEquipment(attacker);
            attacker.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.IRON_SWORD));
            attacker.setItemSlot(
                    EquipmentSlot.LEGS,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.TIBAULTS_WILL
                    )
            );
            DamageSettlementSnapshot tibaultInactive = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            attacker.addEffect(new MobEffectInstance(
                    MobEffects.RESISTANCE, 200, 0, false, false));
            DamageSettlementSnapshot tibaultActive = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            assertPhysicalAndTotal(tibaultInactive, 10.0F,
                    "tibault inactive");
            assertPhysicalAndTotal(tibaultActive, 12.0F,
                    "tibault unstoppable");
            attacker.removeAllEffects();

            attacker.setItemSlot(
                    EquipmentSlot.LEGS,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.BLOOD_MOON_BREECHES
                    )
            );
            DamageSettlementSnapshot bloodMoonInactive = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            Monster weakTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            weakTarget.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 200, 0, false, false));
            DamageSettlementSnapshot bloodMoonWeakness = submitEquipped(
                    helper, attacker, weakTarget, false);
            Monster witheredTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            witheredTarget.addEffect(new MobEffectInstance(
                    MobEffects.WITHER, 200, 0, false, false));
            DamageSettlementSnapshot bloodMoonWither = submitEquipped(
                    helper, attacker, witheredTarget, false);
            assertPhysicalAndTotal(bloodMoonInactive, 10.0F,
                    "blood moon inactive");
            assertPhysicalAndTotal(bloodMoonWeakness, 12.0F,
                    "blood moon weakness");
            assertPhysicalAndTotal(bloodMoonWither, 12.0F,
                    "blood moon wither");
            weakTarget.removeAllEffects();
            witheredTarget.removeAllEffects();

            attacker.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            attacker.setItemSlot(
                    EquipmentSlot.HEAD,
                    ObeliskUniqueEquipmentStacks.create(
                            ObeliskEquipmentIds.COWL_OF_THE_NAMELESS
                    )
            );
            DamageSettlementSnapshot cowlInactive = submitEquipped(
                    helper,
                    attacker,
                    target(helper, EntityType.ZOMBIE, 1.0F, 0.0D),
                    false
            );
            Monster slowedTarget = target(
                    helper, EntityType.ZOMBIE, 1.0F, 0.0D);
            slowedTarget.addEffect(new MobEffectInstance(
                    MobEffects.SLOWNESS, 200, 0, false, false));
            DamageSettlementSnapshot cowlActive = submitEquipped(
                    helper, attacker, slowedTarget, false);
            assertPhysicalAndTotal(cowlInactive, 10.0F,
                    "cowl inactive");
            assertPhysicalAndTotal(cowlActive, 11.5F,
                    "cowl controlled target");
            slowedTarget.removeAllEffects();

            helper.succeed();
        } finally {
            FORCE_CRITICAL_TARGET.remove();
            attacker.removeAllEffects();
            clearEquipment(attacker);
        }
    }

    private static void uniqueStackAndReward(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess()
                .createSerializationContext(NbtOps.INSTANCE);
        for (ObeliskUniqueEquipmentDefinition definition
                : ObeliskUniqueEquipmentCatalog.all()) {
            ItemStack first = ObeliskUniqueEquipmentStacks.create(definition);
            ItemStack second = ObeliskUniqueEquipmentStacks.create(definition);
            if (first == second) {
                throw new AssertionError(
                        "unique stack factory reused an ItemStack instance: "
                                + definition.templateId());
            }
            assertSameStack(first, second,
                    "deterministic unique stack " + definition.templateId());
            if (!first.is(definition.baseItem().get())) {
                throw new AssertionError(
                        "unique stack used the wrong base item: "
                                + definition.templateId());
            }
            if (!definition.assetId()
                    .equals(first.get(DataComponents.ITEM_MODEL))) {
                throw new AssertionError(
                        "unique stack used the wrong item model: "
                                + definition.templateId());
            }
            if (definition.slot() != ObeliskEquipmentSlot.WEAPON) {
                var equippable = first.get(DataComponents.EQUIPPABLE);
                if (equippable == null
                        || equippable.assetId().isEmpty()
                        || !equippable.assetId().get().equals(definition.equipmentAsset())) {
                    throw new AssertionError(
                            "unique armor used the wrong equipment asset: "
                                    + definition.templateId()
                    );
                }
            }
            if (!Component.translatable(definition.displayNameTranslationKey())
                    .equals(first.get(DataComponents.CUSTOM_NAME))) {
                throw new AssertionError(
                        "unique stack used the wrong translated name: "
                                + definition.templateId());
            }
            if (!ObeliskUniqueEquipmentStacks.identify(first)
                    .map(ObeliskUniqueEquipmentDefinition::templateId)
                    .filter(definition.templateId()::equals)
                    .isPresent()) {
                throw new AssertionError(
                        "unique stack identity was not recoverable: "
                                + definition.templateId());
            }
            if (DamageNexusTemplates.entry(definition.templateId()).isEmpty()) {
                throw new AssertionError(
                        "unique template was not registered: "
                                + definition.templateId());
            }
            if (uniqueReferences(first).size() != 1
                    || !uniqueReferences(first).getFirst()
                    .equals(definition.templateId())) {
                throw new AssertionError(
                        "unique stack did not contain exactly one identity: "
                                + definition.templateId());
            }

            Tag encoded = ItemStack.CODEC.encodeStart(ops, first).getOrThrow();
            ItemStack restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
            assertSameStack(first, restored,
                    "unique stack codec " + definition.templateId());
            if (!ObeliskUniqueEquipmentStacks.identify(restored)
                    .map(ObeliskUniqueEquipmentDefinition::templateId)
                    .filter(definition.templateId()::equals)
                    .isPresent()) {
                throw new AssertionError(
                        "unique identity did not survive ItemStack codec: "
                                + definition.templateId());
            }
        }

        Identifier foreignEntry = Identifier.fromNamespaceAndPath(
                "thirdpartymod", "preserved_unique_entry");
        Identifier foreignAffix = Identifier.fromNamespaceAndPath(
                "thirdpartymod", "preserved_unique_affix");
        ItemStack foreignBase = new ItemStack(Items.NETHERITE_SWORD);
        DamageNexusItemApi.setTemplateReferences(
                foreignBase,
                new DamageItemTemplateReferences(
                        List.of(new DamageEntryTemplateReference(foreignEntry)),
                        List.of(new DamageAffixTemplateReference(foreignAffix))
                )
        );
        ItemStack grandfather = ObeliskUniqueEquipmentStacks.copyWithIdentity(
                foreignBase,
                ObeliskUniqueEquipmentCatalog.find(
                        ObeliskEquipmentIds.GRANDFATHER
                ).orElseThrow()
        );
        if (DamageNexusItemApi.getEntryTemplateReferences(grandfather).stream()
                .noneMatch(reference -> reference.id().equals(foreignEntry))
                || DamageNexusItemApi.getAffixTemplateReferences(grandfather)
                .stream()
                .noneMatch(reference -> reference.id().equals(foreignAffix))) {
            throw new AssertionError(
                    "unique stack construction removed foreign references");
        }
        if (ObeliskUniqueEquipmentStacks.identify(foreignBase).isPresent()) {
            throw new AssertionError(
                    "foreign-only references were mistaken for a unique identity");
        }
        ItemStack ambiguous = grandfather.copy();
        DamageNexusItemApi.addEntryTemplateReference(
                ambiguous,
                new DamageEntryTemplateReference(
                        ObeliskEquipmentIds.TIBAULTS_WILL
                )
        );
        if (ObeliskUniqueEquipmentStacks.identify(ambiguous).isPresent()) {
            throw new AssertionError(
                    "multiple unique identities were guessed instead of rejected");
        }
        DamageItemTemplateReferences beforeRemoval =
                DamageNexusItemApi.getTemplateReferences(grandfather);
        ObeliskEquipmentRules.removeManagedReferences(grandfather);
        if (!beforeRemoval.equals(
                DamageNexusItemApi.getTemplateReferences(grandfather))) {
            throw new AssertionError(
                    "tempering cleanup removed a unique or foreign reference");
        }
        if (ObeliskEquipmentRules.accepts(grandfather)
                || ObeliskEquipmentRules.slot(grandfather).isPresent()
                || ObeliskTemperingRoller.canTemper(grandfather, true)) {
            throw new AssertionError("unique equipment entered tempering");
        }

        UUID rewardUuid = UUID.fromString(
                "11111111-2222-3333-4444-555555555555");
        UUID instanceUuid = UUID.fromString(
                "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ItemStack postProcessed = ObeliskEquipmentGenerator.generateRewardStack(
                grandfather, 123456789L, rewardUuid, instanceUuid, 0);
        assertSameStack(grandfather, postProcessed,
                "unique reward post-processing");
        if (!beforeRemoval.equals(
                DamageNexusItemApi.getTemplateReferences(postProcessed))) {
            throw new AssertionError(
                    "reward post-processing attached tempering to a unique");
        }

        UniqueEquipmentRewardItemSource tierFour =
                new UniqueEquipmentRewardItemSource((context, random) -> 4);
        ItemStack rewardA = tierFour.items(null, new Random(445566L))
                .findFirst()
                .orElseThrow();
        ItemStack rewardB = tierFour.items(null, new Random(445566L))
                .findFirst()
                .orElseThrow();
        assertSameStack(rewardA, rewardB, "deterministic unique reward source");
        if (!ObeliskUniqueEquipmentStacks.isUnique(rewardA)) {
            throw new AssertionError("tier-four source did not create a unique");
        }
        UniqueEquipmentRewardItemSource tierThree =
                new UniqueEquipmentRewardItemSource((context, random) -> 3);
        if (tierThree.items(null, new Random(445566L)).findAny().isPresent()) {
            throw new AssertionError("tier-three source produced a unique");
        }

        helper.succeed();
    }

    private static void assertConditional(
            GameTestHelper helper,
            ArmorStand attacker,
            Identifier template,
            LivingEntity matching,
            float matchingDamage,
            LivingEntity rejected,
            float rejectedDamage,
            String name
    ) {
        DamageSettlementSnapshot active = submit(
                helper, attacker, matching, template, false);
        DamageSettlementSnapshot inactive = submit(
                helper, attacker, rejected, template, false);
        assertClose(active.resolvedDamage(), matchingDamage,
                name + " active");
        assertClose(inactive.resolvedDamage(), rejectedDamage,
                name + " inactive");
    }

    private static void clearEquipment(LivingEntity entity) {
        entity.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
    }

    private static double[] resistanceValues(LivingEntity entity) {
        return new double[] {
                entity.getAttributeValue(DamageNexusAttributes.resistancePhysical()),
                entity.getAttributeValue(DamageNexusAttributes.resistanceFire()),
                entity.getAttributeValue(DamageNexusAttributes.resistanceCold()),
                entity.getAttributeValue(DamageNexusAttributes.resistanceLightning()),
                entity.getAttributeValue(DamageNexusAttributes.resistanceMagic()),
                entity.getAttributeValue(DamageNexusAttributes.resistancePoison()),
                entity.getAttributeValue(DamageNexusAttributes.resistanceWither()),
                entity.getAttributeValue(DamageNexusAttributes.resistanceKinetic())
        };
    }

    private static void assertResistanceValues(
            LivingEntity entity,
            double[] expected,
            String name
    ) {
        double[] actual = resistanceValues(entity);
        if (actual.length != expected.length) {
            throw new AssertionError(name + " value count changed");
        }
        for (int index = 0; index < expected.length; index++) {
            if (Math.abs(actual[index] - expected[index]) > EPSILON) {
                throw new AssertionError(
                        name + " index=" + index
                                + " expected=" + expected[index]
                                + " actual=" + actual[index]
                );
            }
        }
    }

    private static ArmorStand attacker(GameTestHelper helper) {
        ArmorStand attacker = helper.spawn(
                EntityType.ARMOR_STAND, new BlockPos(1, 2, 1));
        attacker.setInvulnerable(true);
        return attacker;
    }

    private static <T extends Monster> T target(
            GameTestHelper helper,
            EntityType<T> type,
            float healthFraction,
            double armor
    ) {
        T target = helper.spawn(type, new BlockPos(3, 2, 3));
        target.setNoAi(true);
        AttributeInstance armorAttribute = target.getAttribute(Attributes.ARMOR);
        if (armorAttribute != null) {
            armorAttribute.setBaseValue(armor);
        }
        AttributeInstance toughness = target.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            toughness.setBaseValue(0.0D);
        }
        target.setHealth(target.getMaxHealth() * healthFraction);
        return target;
    }

    private static DamageSettlementSnapshot submit(
            GameTestHelper helper,
            ArmorStand attacker,
            LivingEntity target,
            Identifier template,
            boolean forceCritical
    ) {
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        if (template != null) {
            addReference(weapon, template);
        }
        attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        attacker.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        return submitEquipped(helper, attacker, target, forceCritical);
    }

    private static DamageSettlementSnapshot submitEquipped(
            GameTestHelper helper,
            ArmorStand attacker,
            LivingEntity target,
            boolean forceCritical
    ) {
        if (forceCritical) {
            FORCE_CRITICAL_TARGET.set(target);
        }
        try {
            DamageRequest request = DamageRequest.builder(
                            helper.getLevel(),
                            target,
                            DamageSourceDescriptor.of(DamageTypes.MOB_ATTACK),
                            10.0F
                    )
                    .kind(DamageRequestKind.PRIMARY)
                    .directEntity(attacker)
                    .logicalAttacker(attacker)
                    .equipmentOwner(attacker)
                    .build();
            DamageResult result = DamageNexusApi.submitDamage(request);
            DamageSettlementSnapshot snapshot = result.settlement()
                    .orElseThrow(() -> new AssertionError(
                            "DamageNexus request had no settlement: status="
                                    + result.status() + " failure="
                                    + result.failure()));
            if (!result.applied() || !snapshot.pipelineExecuted()) {
                throw new AssertionError(
                        "DamageNexus pipeline did not apply: status="
                                + result.status() + " failure="
                                + result.failure()
                );
            }
            return snapshot;
        } finally {
            FORCE_CRITICAL_TARGET.remove();
        }
    }

    private static void addReference(ItemStack stack, Identifier template) {
        if (!DamageNexusItemApi.addEntryTemplateReference(
                stack,
                new DamageEntryTemplateReference(template))) {
            throw new AssertionError(
                    "Unable to write template reference " + template);
        }
    }

    private static List<Identifier> managedReferences(ItemStack stack) {
        return DamageNexusItemApi.getEntryTemplateReferences(stack).stream()
                .map(DamageEntryTemplateReference::id)
                .filter(id -> ObeliskDepths.MOD_ID.equals(id.getNamespace()))
                .toList();
    }

    private static List<Identifier> uniqueReferences(ItemStack stack) {
        return DamageNexusItemApi.getEntryTemplateReferences(stack).stream()
                .map(DamageEntryTemplateReference::id)
                .filter(id -> ObeliskUniqueEquipmentCatalog.find(id).isPresent())
                .toList();
    }

    private static void assertPhysicalAndTotal(
            DamageSettlementSnapshot snapshot,
            float expected,
            String name
    ) {
        assertChannel(snapshot, DamageChannel.PHYSICAL_ID, expected,
                name + " physical channel");
        assertClose(snapshot.resolvedDamage(), expected,
                name + " resolved damage");
    }

    private static void assertChannel(
            DamageSettlementSnapshot snapshot,
            Identifier channel,
            float expected,
            String name
    ) {
        assertClose(
                snapshot.resolvedChannelDamage().getOrDefault(channel, 0.0F),
                expected,
                name
        );
    }

    private static void assertSameStack(
            ItemStack expected,
            ItemStack actual,
            String name
    ) {
        if (expected.getCount() != actual.getCount()
                || !ItemStack.isSameItemSameComponents(expected, actual)) {
            throw new AssertionError(name + " stack mismatch");
        }
    }

    private static void assertClose(float actual, float expected, String name) {
        if (Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(
                    name + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void registerFunction(
            RegisterEvent event,
            ResourceKey<Consumer<GameTestHelper>> key,
            Consumer<GameTestHelper> function
    ) {
        event.register(
                Registries.TEST_FUNCTION,
                key.identifier(),
                () -> function
        );
    }

    private static void registerTest(
            RegisterGameTestsEvent event,
            ResourceKey<Consumer<GameTestHelper>> function,
            TestData<Holder<TestEnvironmentDefinition<?>>> data
    ) {
        event.registerTest(
                function.identifier(),
                new FunctionGameTestInstance(function, data)
        );
    }

    private static ResourceKey<Consumer<GameTestHelper>> functionKey(
            String path
    ) {
        return ResourceKey.create(Registries.TEST_FUNCTION, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
