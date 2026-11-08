package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mod(modid = AutoPvPMod.MODID, name = AutoPvPMod.NAME, version = AutoPvPMod.VERSION)
public class AutoPvPMod {

    public static final String MODID = "autopvp";
    public static final String NAME = "Auto PvP";
    public static final String VERSION = "3.0"; // KillAura Logic

    private static KeyBinding keyToggleAura;
    private static KeyBinding keyToggleCrits;

    private static boolean isAuraActive = false;
    private static boolean isCritsActive = false;

    // Радиус атаки (можно менять)
    private static final double ATTACK_RANGE = 3.8;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleAura = new KeyBinding("key.autopvp.aura", Keyboard.KEY_X, "key.categories.combat");
        keyToggleCrits = new KeyBinding("key.autopvp.crits", Keyboard.KEY_R, "key.categories.combat");

        ClientRegistry.registerKeyBinding(keyToggleAura);
        ClientRegistry.registerKeyBinding(keyToggleCrits);

        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v3.0 (KillAura Edition) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyToggleAura.isPressed()) {
            isAuraActive = !isAuraActive;
            String msg = "KillAura: " + (isAuraActive ? "§aВключена" : "§cВыключена");
            mc.player.sendMessage(new TextComponentString(msg));
            if (!isAuraActive) {
                isCritsActive = false;
                mc.player.sendMessage(new TextComponentString("§eCriticals: §cАвтоматически выключены"));
            }
        }

        if (keyToggleCrits.isPressed()) {
            if (!isAuraActive) {
                mc.player.sendMessage(new TextComponentString("§cСначала включите KillAura (Клавиша X)"));
                return;
            }
            isCritsActive = !isCritsActive;
            String msg = "Criticals: " + (isCritsActive ? "§aВключены" : "§cВыключены");
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isAuraActive) return;

        // 1. Проверяем кулдаун атаки. Если меч не заряжен на 100% — ничего не делаем.
        // Это самый важный пункт для "легитимности" и прохождения урона.
        if (player.getAttackCooldownProgress(0.5f) < 1.0f) {
            return;
        }

        // 2. Ищем ближайшую живую цель в радиусе
        EntityLivingBase target = findClosestTarget(player);
        if (target == null) return;

        // 3. Если включены криты, прыгаем ПЕРЕД атакой
        if (isCritsActive && player.onGround && !player.isInWater() && !player.isOnLadder()) {
            player.jump();
        }

        // 4. Атакуем НАТИВНЫМ методом игры (как при клике ЛКМ)
        Minecraft.getMinecraft().playerController.attackEntity(player, target);
        
        // 5. Взмах рукой для визуала (сервер и так увидит атаку)
        player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
    }

    /**
     * Находит ближайшую сущность, которую можно атаковать.
     * Использует стримы и компаратор для оптимизации.
     */
    private EntityLivingBase findClosestTarget(EntityPlayer player) {
        // Получаем список всех сущностей в радиусе
        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(
                player,
                player.getEntityBoundingBox().grow(ATTACK_RANGE)
        );

        return entities.stream()
                .filter(e -> e instanceof EntityLivingBase) // Только живые
                .map(e -> (EntityLivingBase) e) // Приводим тип
                .filter(e -> e.isEntityAlive() && e.getDistance(player) <= ATTACK_RANGE) // Фильтр по дистанции и жизни
                .min(Comparator.comparingDouble(e -> e.getDistanceSq(player))) // Берем ближайшего (сравниваем квадраты расстояний для скорости)
                .orElse(null);
    }
}
