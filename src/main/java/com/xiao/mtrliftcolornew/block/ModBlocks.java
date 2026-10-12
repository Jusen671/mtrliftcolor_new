package com.xiao.mtrliftcolornew.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * 本模组新增的方块。目前只有「电梯维护围挡」，共三个 id、两种造型：
 *
 * <ul>
 *   <li>{@code mtrliftcolor:lift_maintenance_barrier} 与
 *       {@code mtrliftcolor:lift_maintenance_barrier_90} —— 90° 围挡：左右两片布各折 45°；</li>
 *   <li>{@code mtrliftcolor:lift_maintenance_barrier_45} —— 45° 围挡：左右两片布各折 22.5°。</li>
 * </ul>
 *
 * <p>它们都是隐藏的测试方块，不加入任何创造模式物品栏，只能用指令获取。</p>
 */
public final class ModBlocks {

    /** 方块用的命名空间（玩家指定的 {@code mtrliftcolor}）。 */
    public static final String NAMESPACE = "mtrliftcolor";

    /** 每片侧布相对中片折起的角度。 */
    public static final float FOLD_90 = 45.0F;
    public static final float FOLD_45 = 22.5F;

    public static final Block LIFT_MAINTENANCE_BARRIER = barrier(FOLD_90);
    public static final Block LIFT_MAINTENANCE_BARRIER_90 = barrier(FOLD_90);
    public static final Block LIFT_MAINTENANCE_BARRIER_45 = barrier(FOLD_45);

    public static final BlockEntityType<LiftMaintenanceBarrierBlockEntity> LIFT_MAINTENANCE_BARRIER_BE =
            FabricBlockEntityTypeBuilder.create(
                    LiftMaintenanceBarrierBlockEntity::new,
                    LIFT_MAINTENANCE_BARRIER,
                    LIFT_MAINTENANCE_BARRIER_90,
                    LIFT_MAINTENANCE_BARRIER_45).build();

    private ModBlocks() {
    }

    private static Block barrier(float foldDegrees) {
        return new LiftMaintenanceBarrierBlock(foldDegrees, AbstractBlock.Settings.create()
                .mapColor(MapColor.YELLOW)
                .strength(0.8F)
                .sounds(BlockSoundGroup.WOOL)
                .nonOpaque());
    }

    /** 在主入口调用一次，把方块、对应物品与方块实体类型注册进游戏。 */
    public static void register() {
        registerBlock("lift_maintenance_barrier", LIFT_MAINTENANCE_BARRIER);
        registerBlock("lift_maintenance_barrier_90", LIFT_MAINTENANCE_BARRIER_90);
        registerBlock("lift_maintenance_barrier_45", LIFT_MAINTENANCE_BARRIER_45);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id("lift_maintenance_barrier"),
                LIFT_MAINTENANCE_BARRIER_BE);
    }

    private static void registerBlock(String path, Block block) {
        Registry.register(Registries.BLOCK, id(path), block);
        Registry.register(Registries.ITEM, id(path), new BlockItem(block, new Item.Settings()));
    }

    private static Identifier id(String path) {
        return new Identifier(NAMESPACE, path);
    }
}
