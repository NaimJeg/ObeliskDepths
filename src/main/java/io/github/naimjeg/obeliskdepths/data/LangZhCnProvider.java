package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Simplified Chinese translations generated alongside the English language
 * file. The language resource parity test verifies that both locales expose
 * the same keys and formatting arguments.
 */
public final class LangZhCnProvider extends LanguageProvider {
    private static final String LOCALE = "zh_cn";

    public LangZhCnProvider(PackOutput output) {
        super(output, ObeliskDepths.MOD_ID, LOCALE);
    }

    @Override
    protected void addTranslations() {
        addBlocks();
        addItemsAndContainers();
        addTempering();
        addUniqueEquipment();
        addPortalUi();
        addMessages();
    }

    private void addBlocks() {
        add("block.obeliskdepths.amphixylon_door", "双生木门");
        add("block.obeliskdepths.amphixylon_fence", "双生木栅栏");
        add("block.obeliskdepths.amphixylon_fence_gate", "双生木栅栏门");
        add("block.obeliskdepths.amphixylon_leaves", "双生木树叶");
        add("block.obeliskdepths.amphixylon_log", "双生木原木");
        add("block.obeliskdepths.amphixylon_planks", "双生木木板");
        add("block.obeliskdepths.amphixylon_root_tangle", "双生木盘根");
        add("block.obeliskdepths.amphixylon_slab", "双生木台阶");
        add("block.obeliskdepths.amphixylon_stairs", "双生木楼梯");
        add("block.obeliskdepths.amphixylon_trapdoor", "双生木活板门");
        add("block.obeliskdepths.amphixylon_vine_bulb", "双生木藤蔓球茎");
        add("block.obeliskdepths.amphixylon_wood", "双生木");
        add("block.obeliskdepths.dungeon_bricks", "地牢石砖");
        add("block.obeliskdepths.dungeon_bricks_slab", "地牢石砖台阶");
        add("block.obeliskdepths.dungeon_bricks_stairs", "地牢石砖楼梯");
        add("block.obeliskdepths.dungeon_bricks_wall", "地牢石砖墙");
        add("block.obeliskdepths.dungeon_cracked_bricks", "裂纹地牢石砖");
        add("block.obeliskdepths.dungeon_cracked_tiles", "裂纹地牢砖瓦");
        add("block.obeliskdepths.dungeon_lamp", "地牢灯");
        add("block.obeliskdepths.lit_dungeon_lamp", "常亮地牢灯");
        add("block.obeliskdepths.dungeon_stone", "地牢石");
        add("block.obeliskdepths.dungeon_stone_slab", "地牢石台阶");
        add("block.obeliskdepths.dungeon_stone_stairs", "地牢石楼梯");
        add("block.obeliskdepths.dungeon_stone_wall", "地牢石墙");
        add("block.obeliskdepths.dungeon_tiles", "地牢砖瓦");
        add("block.obeliskdepths.dungeon_tiles_slab", "地牢砖瓦台阶");
        add("block.obeliskdepths.dungeon_tiles_stairs", "地牢砖瓦楼梯");
        add("block.obeliskdepths.dungeon_tiles_wall", "地牢砖瓦墙");
        add("block.obeliskdepths.great_swamp_coarse_dirt", "大沼泽砂土");
        add("block.obeliskdepths.great_swamp_dirt", "大沼泽泥土");
        add("block.obeliskdepths.great_swamp_grass_block", "大沼泽草方块");
        add("block.obeliskdepths.great_swamp_mud", "大沼泽泥巴");
        add("block.obeliskdepths.great_swamp_vines", "大沼泽藤蔓");
        add("block.obeliskdepths.obelisk", "方尖碑");
        add("block.obeliskdepths.obelisk_chest", "方尖碑宝箱");
        add("block.obeliskdepths.obelisk_smithing_table", "方尖碑锻造台");
        add("block.obeliskdepths.reinforced_dungeon_stone", "强化地牢石");
        add("block.obeliskdepths.stripped_amphixylon_log", "去皮双生木原木");
        add("block.obeliskdepths.stripped_amphixylon_wood", "去皮双生木");
    }

    private void addItemsAndContainers() {
        add("container.obeliskdepths.obelisk_portal", "方尖碑传送门");
        add("container.obeliskdepths.obelisk_tempering", "方尖碑淬炼");
        add("item.obeliskdepths.exile_boots", "流放者靴子");
        add("item.obeliskdepths.exile_chestplate", "流放者胸甲");
        add("item.obeliskdepths.exile_helmet", "流放者头盔");
        add("item.obeliskdepths.exile_leggings", "流放者护腿");
        add("item.obeliskdepths.return_scroll", "归返卷轴");
        add("item.obeliskdepths.tempering_smithing_template", "淬炼升级");
        add("itemGroup.obeliskdepths.building_blocks", "方尖碑深渊：建筑方块");
        add("itemGroup.obeliskdepths.obelisk_items", "方尖碑深渊：物品");
    }

    private void addTempering() {
        add("gui.obeliskdepths.tempering.directions", "淬炼方向");
        add("gui.obeliskdepths.tempering.invalid_recipe", "没有匹配的淬炼配方");
        add("gui.obeliskdepths.tempering.no_possible_affixes", "没有可用词缀");
        add("gui.obeliskdepths.tempering.possible_affixes", "可能的词缀");
        add("gui.obeliskdepths.tempering.preview_unavailable", "无法预览词缀池");

        addTemperingDirection(
                "arcane",
                "奥秘",
                "强化魔法伤害及魔刃类伤害增益。"
        );
        addTemperingDirection(
                "balance",
                "均衡",
                "稳定淬炼装备，提供可靠的通用伤害。"
        );
        addTemperingDirection(
                "edge",
                "锋刃",
                "强化物理进攻、削弱护甲并提升终结能力。"
        );
        addTemperingDirection(
                "flame",
                "烈焰",
                "强化火焰伤害以及对燃烧目标的压制力。"
        );
        addTemperingDirection(
                "frost",
                "寒霜",
                "强化寒冷伤害以及物理伤害向寒冷伤害的转化。"
        );
        addTemperingDirection(
                "hunt",
                "狩猎",
                "强化首领狩猎以及对虚弱目标的处决能力。"
        );
        addTemperingDirection(
                "precision",
                "精准",
                "强化暴击以及先手攻击的压制力。"
        );
        addTemperingDirection(
                "storm",
                "风暴",
                "以闪电和动能伤害强化猛烈打击。"
        );
        addTemperingDirection(
                "venom",
                "毒蚀",
                "强化毒素、凋零以及额外毒素伤害。"
        );

        add("tooltip.obeliskdepths.tempering_template.tier", "淬炼等级：%s");
        add("tooltip.obeliskdepths.tempering_template.weight", "淬炼权重：%s");

        addTemperingEntry(
                "ambushers",
                "伏击者的",
                "目标生命值高于 80% 时，+18% 全局伤害",
                "为首次精准命中留下的先手印记。"
        );
        addTemperingEntry(
                "arcane",
                "奥秘",
                "+3 魔法伤害",
                "将魔力贯穿攻击的专注符印。"
        );
        addTemperingEntry(
                "brutal",
                "残暴",
                "+10% 全局伤害",
                "让每次攻击都更加沉重的蛮力印记。"
        );
        addTemperingEntry(
                "critical_edge",
                "暴击之刃",
                "最终暴击判定为真时，+20% 物理伤害",
                "奖励干净利落一击的淬炼印记。"
        );
        addTemperingEntry(
                "deadly",
                "致命",
                "最终暴击判定为真时，+20% 物理伤害",
                "奖励果断出手时机的精准印记。"
        );
        addTemperingEntry(
                "executioners",
                "处刑者的",
                "目标生命值低于 35% 时，+20% 物理伤害",
                "专为终结虚弱敌人而生的印记。"
        );
        addTemperingEntry(
                "fire_edge",
                "炎刃",
                "+4 火焰伤害",
                "让烈火沿武器锋刃燃烧的淬炼印记。"
        );
        add("entry.obeliskdepths.fire_edge.tooltip.1", "+15% 火焰伤害");
        addTemperingEntry(
                "flameforged",
                "炎铸",
                "将 20% 物理伤害转化为火焰伤害",
                "从熔炉中诞生、改变利刃伤害性质的印记。"
        );
        addTemperingEntry(
                "flaming",
                "燃烧",
                "+3 火焰伤害",
                "稳定附加火焰伤害的直接余烬印记。"
        );
        addTemperingEntry(
                "frostbound",
                "霜缚",
                "+3 寒冷伤害",
                "将严冬注入武器锋刃的寒冷印记。"
        );
        addTemperingEntry(
                "frostforged",
                "霜铸",
                "将 20% 物理伤害转化为寒冷伤害",
                "将冲击化为寒意的苍白锻造印记。"
        );
        addTemperingEntry(
                "giant_slayers",
                "巨人杀手的",
                "对首领造成的全局伤害 +20%",
                "为狩猎本不该屹立之物而制的印记。"
        );
        addTemperingEntry(
                "impacting",
                "冲击",
                "+2.5 动能伤害",
                "为攻击增添钝击动量的震荡印记。"
        );
        addTemperingEntry(
                "piercing",
                "穿透",
                "+1.5 物理真实伤害",
                "从狭窄一点贯入、绕过防护的力量。"
        );
        addTemperingEntry(
                "razor_edged",
                "锐利",
                "+12% 物理伤害",
                "用锋芒直接解决问题的利刃印记。"
        );
        addTemperingEntry(
                "smoldering",
                "闷燃",
                "对燃烧目标造成的全局伤害 +15%",
                "让敌人持续燃烧便会得到回报的余热印记。"
        );
        addTemperingEntry(
                "spellblade",
                "魔刃",
                "获得物理伤害 15% 的额外魔法伤害",
                "将力量回响为魔法的刀锋符印。"
        );
        addTemperingEntry(
                "stormcharged",
                "风暴充能",
                "+3 闪电伤害",
                "随攻击迸发电光的充能印记。"
        );
        addTemperingEntry(
                "stormforged",
                "雷铸",
                "将 18% 物理伤害转化为闪电伤害",
                "将力量化为耀眼雷光的风暴印记。"
        );
        addTemperingEntry(
                "sundering",
                "破甲",
                "目标护甲效果 -12%",
                "让护甲难以抵御攻击的破坏印记。"
        );
        addTemperingEntry(
                "tempered",
                "淬炼",
                "+3 物理伤害",
                "不依赖苛刻条件的稳定攻击印记。"
        );
        addTemperingEntry(
                "toxic_edge",
                "毒刃",
                "获得物理伤害 15% 的额外毒素伤害",
                "让利落切口染上剧毒的涂层锋刃印记。"
        );
        addTemperingEntry(
                "venomous",
                "剧毒",
                "+3 毒素伤害",
                "留下苦涩伤口的毒素印记。"
        );
        addTemperingEntry(
                "withering",
                "凋零",
                "+2 凋零伤害",
                "带着枯败侵蚀之力的衰亡印记。"
        );
    }

    private void addUniqueEquipment() {
        addUniqueEquipment(
                "grandfather",
                "祖父",
                java.util.List.of("最终暴击判定为真时，伤害提高 50%"),
                "这柄先祖之刃的裁决，比每一任持有者都更长久。"
        );
        addUniqueEquipment(
                "harlequin_crest",
                "谐角之冠",
                java.util.List.of("受到的伤害降低 10%"),
                "嘲弄般的冠冕，让致命一击也沦为空洞威胁。"
        );
        addUniqueEquipment(
                "tyraels_might",
                "泰瑞尔之力",
                java.util.List.of(
                        "所有受支持伤害频道的抗性等级 +10",
                        "生命值高于 99% 时，+4 魔法伤害"
                ),
                "无所畏惧地肩负正义，才知其真正重量。"
        );
        addUniqueEquipment(
                "tibaults_will",
                "提博特的意志",
                java.util.List.of("不可阻挡时，造成的伤害提高 20%"),
                "束缚崩解之时，抗争便化为势不可挡的力量。"
        );
        addUniqueEquipment(
                "blood_moon_breeches",
                "血月马裤",
                java.util.List.of("对受诅咒目标造成的伤害提高 20%"),
                "血月之下，每一道诅咒都会撕开更深的伤口。"
        );
        addUniqueEquipment(
                "cowl_of_the_nameless",
                "无名者兜帽",
                java.util.List.of("对受控目标造成的伤害提高 15%"),
                "失去行动能力之人，很快也会失去自己的名字。"
        );
    }

    private void addPortalUi() {
        add("event.obeliskdepths.dungeon_raid", "地牢突袭 — 击杀 %1$s/%2$s");

        add("gui.obeliskdepths.dungeon_loading.awaiting_client", "正在打开加载界面……");
        add("gui.obeliskdepths.dungeon_loading.cancelled", "已取消进入地牢。");
        add("gui.obeliskdepths.dungeon_loading.completed", "已进入地牢。");
        add("gui.obeliskdepths.dungeon_loading.failed", "进入地牢失败。");
        add("gui.obeliskdepths.dungeon_loading.finalizing", "正在完成地牢进入流程……");
        add("gui.obeliskdepths.dungeon_loading.preparing", "正在准备地牢入口……");
        add("gui.obeliskdepths.dungeon_loading.ready", "目的地已就绪……");
        add("gui.obeliskdepths.dungeon_loading.teleporting", "正在进入地牢……");
        add("gui.obeliskdepths.dungeon_loading.title", "进入地牢");

        add("gui.obeliskdepths.portal.cancelled", "激活已取消。");
        add("gui.obeliskdepths.portal.cancelled.dimension_changed", "维度已改变。");
        add("gui.obeliskdepths.portal.cancelled.level_unloaded", "地牢维度已卸载。");
        add("gui.obeliskdepths.portal.cancelled.menu_closed", "界面已关闭。");
        add("gui.obeliskdepths.portal.cancelled.moved_too_far", "距离方尖碑太远。");
        add("gui.obeliskdepths.portal.cancelled.obelisk_invalid", "方尖碑已失效。");
        add("gui.obeliskdepths.portal.cancelled.player_died", "玩家死亡，准备已取消。");
        add("gui.obeliskdepths.portal.cancelled.player_disconnected", "玩家已断开连接。");
        add("gui.obeliskdepths.portal.cancelled.server_stopping", "服务器正在停止。");
        add("gui.obeliskdepths.portal.cancelled.timeout", "准备超时。");
        add("gui.obeliskdepths.portal.cancelled.user", "激活已取消。");

        add("gui.obeliskdepths.portal.failed", "激活失败。");
        add("gui.obeliskdepths.portal.failed.chunk_load", "区块准备失败。");
        add("gui.obeliskdepths.portal.failed.commit_validation", "提交前激活状态已改变。");
        add("gui.obeliskdepths.portal.failed.entry_validation", "目的地验证失败。");
        add("gui.obeliskdepths.portal.failed.internal", "激活时发生内部错误。");
        add("gui.obeliskdepths.portal.failed.invalid_tribute", "贡品无效。");
        add("gui.obeliskdepths.portal.failed.job_missing", "地牢准备状态已丢失。");
        add("gui.obeliskdepths.portal.failed.no_site", "没有可用地点。");
        add("gui.obeliskdepths.portal.failed.non_authoritative_site", "地点元数据无效。");
        add("gui.obeliskdepths.portal.failed.portal_spawn", "传送门创建失败。");
        add("gui.obeliskdepths.portal.failed.prepared_entry", "已准备入口注册失败。");
        add("gui.obeliskdepths.portal.failed.runtime_unavailable", "地牢准备功能已不可用。");
        add("gui.obeliskdepths.portal.failed.site_claim_lost", "地牢地点占用权已丢失。");
        add("gui.obeliskdepths.portal.failed.site_conflict", "地点已被占用。");
        add("gui.obeliskdepths.portal.failed.structure_invalid", "结构元数据无效。");
        add("gui.obeliskdepths.portal.failed.structure_missing", "未找到结构。");
        add("gui.obeliskdepths.portal.failed.submission_rejected", "地牢准备请求被拒绝。");

        add("gui.obeliskdepths.portal.loading", "正在准备地牢……");
        add("gui.obeliskdepths.portal.mode.solo", "单人");
        add("gui.obeliskdepths.portal.note", "模式仅控制传送门的进入方式。");
        add("gui.obeliskdepths.portal.selected.solo", "已选择：单人传送门");

        add("gui.obeliskdepths.portal.stage.committing", "正在开启传送门……");
        add(
                "gui.obeliskdepths.portal.stage.entry_chunks.progress",
                "正在准备入口区块：%1$s / %2$s"
        );
        add("gui.obeliskdepths.portal.stage.generating_start", "正在生成起始区域……");
        add(
                "gui.obeliskdepths.portal.stage.generation.progress",
                "正在准备地点：%1$s / %2$s"
        );
        add("gui.obeliskdepths.portal.stage.planning_entry", "正在规划入口区块……");
        add("gui.obeliskdepths.portal.stage.preparing_entry", "正在准备入口……");
        add("gui.obeliskdepths.portal.stage.queued", "正在排队……");
        add("gui.obeliskdepths.portal.stage.reading_start", "正在读取结构……");
        add("gui.obeliskdepths.portal.stage.ready", "已就绪。");
        add("gui.obeliskdepths.portal.stage.ready_to_commit", "正在完成准备……");
        add("gui.obeliskdepths.portal.stage.requesting_entry", "正在请求入口区块……");
        add("gui.obeliskdepths.portal.stage.requesting_start", "正在请求入口区块……");
        add("gui.obeliskdepths.portal.stage.scanning", "正在扫描地点……");
        add(
                "gui.obeliskdepths.portal.stage.scanning.progress",
                "正在定位地牢：%1$s / %2$s"
        );
        add("gui.obeliskdepths.portal.stage.selecting", "正在选择地点……");
        add("gui.obeliskdepths.portal.stage.unknown", "正在准备地牢……");
        add("gui.obeliskdepths.portal.stage.validating", "正在验证……");
        add("gui.obeliskdepths.portal.stage.validating_chunks", "正在验证入口区块……");
        add("gui.obeliskdepths.portal.stage.validating_entry", "正在验证目的地……");
        add(
                "gui.obeliskdepths.portal.stage.validating_entry.progress",
                "正在验证目的地：%1$s / %2$s"
        );
        add("gui.obeliskdepths.portal.stage.waiting_entry", "正在准备入口区块……");
        add("gui.obeliskdepths.portal.stage.waiting_start", "正在准备入口区块……");
        add("gui.obeliskdepths.portal.start", "开始");
        add("gui.obeliskdepths.portal.tribute", "贡品");
    }

    private void addMessages() {
        add("message.obeliskdepths.dungeon.boundary_warning", "你已离开自己的地牢边界。");
        add(
                "message.obeliskdepths.dungeon.encounter_failed",
                "地牢遭遇战失败。正在将你送回安全地点。"
        );
        add("message.obeliskdepths.obelisk.activation_failed", "未能开启地牢。");
        add(
                "message.obeliskdepths.obelisk.inside_dungeon_denied",
                "无法在地牢内使用方尖碑。"
        );
        add("message.obeliskdepths.obelisk.invalid_obelisk", "方尖碑已失效。");
        add("message.obeliskdepths.obelisk.invalid_tribute", "贡品无效。");
        add(
                "message.obeliskdepths.obelisk.no_dimension",
                "未找到方尖碑深渊维度。"
        );
        add(
                "message.obeliskdepths.portal.entry.access_denied",
                "你不能进入这个地牢传送门。"
        );
        add(
                "message.obeliskdepths.portal.entry.bound_elsewhere",
                "你已绑定至另一个地牢。"
        );
        add(
                "message.obeliskdepths.portal.entry.client_not_ready",
                "地牢加载界面未能及时打开。"
        );
        add(
                "message.obeliskdepths.portal.entry.destination_not_prepared",
                "这个地牢传送门尚未就绪。"
        );
        add(
                "message.obeliskdepths.portal.entry.destination_stabilizing",
                "地牢入口正在稳定。"
        );
        add(
                "message.obeliskdepths.portal.entry.destination_unavailable",
                "没有安全的地牢进入位置。"
        );
        add(
                "message.obeliskdepths.portal.entry.instance_missing",
                "这个地牢已不可用。"
        );
        add(
                "message.obeliskdepths.portal.entry.operation_active",
                "进入地牢的流程已在进行中。"
        );
        add(
                "message.obeliskdepths.portal.entry.operation_started",
                "已开始进入地牢。"
        );
        add(
                "message.obeliskdepths.portal.entry.player_unavailable",
                "玩家已不可用，进入地牢的流程已取消。"
        );
        add(
                "message.obeliskdepths.portal.entry.portal_invalid",
                "地牢传送门已失效。"
        );
        add(
                "message.obeliskdepths.portal.entry.preparation_failed",
                "未能准备地牢入口。"
        );
        add(
                "message.obeliskdepths.portal.entry.registration_failed",
                "未能登记地牢入口。"
        );
        add(
                "message.obeliskdepths.portal.entry.session_expired",
                "这个地牢传送门已过期。"
        );
        add(
                "message.obeliskdepths.portal.entry.session_missing",
                "这个地牢传送门已不再激活。"
        );
        add("message.obeliskdepths.portal.entry.success", "已进入地牢。");
        add(
                "message.obeliskdepths.portal.entry.teleport_failed",
                "未能进入地牢。"
        );
        add(
                "message.obeliskdepths.portal.entry.wrong_source_dimension",
                "这个地牢传送门属于另一个维度。"
        );
        add(
                "message.obeliskdepths.portal.no_anchor",
                "未找到放置地牢传送门的安全位置。"
        );
        add(
                "message.obeliskdepths.portal.no_site",
                "附近未找到尚未抵达的地牢地点。"
        );
        add(
                "message.obeliskdepths.portal.opened",
                "地牢传送门已开启。踏入传送门即可进入。"
        );
        add(
                "message.obeliskdepths.portal.spawn_failed",
                "未能创建地牢传送门。"
        );
        add(
                "message.obeliskdepths.return_scroll.dungeon_level_missing",
                "方尖碑深渊维度不可用。"
        );
        add(
                "message.obeliskdepths.return_scroll.emergency_fallback",
                "已通过紧急目的地离开方尖碑深渊。"
        );
        add(
                "message.obeliskdepths.return_scroll.incomplete_data",
                "你的地牢返回点数据不完整。"
        );
        add(
                "message.obeliskdepths.return_scroll.no_binding",
                "你尚未绑定有效的地牢返回点。"
        );
        add(
                "message.obeliskdepths.return_scroll.no_safe_destination",
                "没有可用的安全返回目的地。"
        );
        add(
                "message.obeliskdepths.return_scroll.not_in_depths",
                "归返卷轴只能在方尖碑深渊中响应。"
        );
        add(
                "message.obeliskdepths.return_scroll.return_level_missing",
                "已记录的返回维度不可用。"
        );
        add(
                "message.obeliskdepths.return_scroll.success",
                "已从方尖碑深渊返回。"
        );
        add("message.obeliskdepths.return_scroll.teleport_failed", "返回失败。");
    }

    private void addTemperingDirection(
            String key,
            String name,
            String description
    ) {
        String prefix = "tempering_direction." + ObeliskDepths.MOD_ID + "." + key;
        add(prefix, name);
        add(prefix + ".description", description);
    }

    private void addTemperingEntry(
            String key,
            String name,
            String tooltip,
            String flavor
    ) {
        String prefix = "entry." + ObeliskDepths.MOD_ID + "." + key;
        add(prefix + ".name", name);
        add(prefix + ".tooltip.0", tooltip);
        add(prefix + ".flavor", flavor);
    }

    private void addUniqueEquipment(
            String key,
            String name,
            java.util.List<String> tooltips,
            String flavor
    ) {
        String itemKey = "item." + ObeliskDepths.MOD_ID + ".unique." + key;
        String entryKey = "entry." + ObeliskDepths.MOD_ID + ".unique." + key;
        add(itemKey, name);
        add(entryKey + ".name", name);
        for (int index = 0; index < tooltips.size(); index++) {
            add(entryKey + ".tooltip." + index, tooltips.get(index));
        }
        add(entryKey + ".flavor", flavor);
    }
}
