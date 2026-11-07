package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

@Mod(modid = AutoPvPMod.MODID, name = AutoPvPMod.NAME, version = AutoPvPMod.VERSION)
public class AutoPvPMod {

    public static final String MODID = "autopvp";
    public static final String NAME = "Auto PvP";
    public static final String VERSION = "2.0";

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleCrit;

    private static boolean isModActive = false;
    private static boolean isCritActive = false;
    private static boolean waitingForFall = false;

    private static int attackTimer = 0;
    private static final int ATTACK_DELAY = 12;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        keyToggleCrit = new KeyBinding("key.autopvp.crit", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleCrit);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v2.0 (Perfect Reach & Crits) загружен!");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            if (!isModActive) {
                isCritActive = false;
                waitingForFall = false;
                mc.player.sendMessage(new TextComponentString("§eКритические прыжки: §cАвтоматически выключены"));
            }
            attackTimer = 0; 
        }

        if (keyToggleCrit.isPressed()) {
            if (!isModActive) {
                mc.player.sendMessage(new TextComponentString("§cСначала включите AutoPvP (Клавиша X)"));
                return;
            }
            isCritActive = !isCritActive;
            waitingForFall = false;
            String msg = "§eКритические прыжки: " + (isCritActive ? "§aВключены" : "§cВыключены");
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // --- Логика ожидания падения для крита ---
        if (waitingForFall) {
            // Критический удар возможен только когда игрок падает (motionY < 0) и не на земле
            if (player.motionY < 0 && !player.onGround) {
                Entity target = findTarget(player);
                if (target != null) {
                    performAttack(player, target); // Бьем напрямую пакетом
                    waitingForFall = false;
                    attackTimer = 0;
                } else {
                    // Цель убежала, сбрасываем ожидание
                    waitingForFall = false;
                }
            } else if (player.onGround) {
                // Приземлились, а удара так и не было — сбрасываем
                waitingForFall = false;
            }
            return; // Пока ждем падения, ничего больше не делаем
        }

        // --- Обычная логика атаки по таймеру ---
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }
        if (attackTimer < ATTACK_DELAY) return;

        Entity target = findTarget(player);
        if (target == null) return;

        if (isCritActive && player.onGround && !player.isInWater() && !player.isOnLadder()) {
            // Прыгаем для крита. Удар пока НЕ наносим.
            player.jump();
            waitingForFall = true;
        } else {
            // Обычный удар (без критов)
            performAttack(player, target);
            attackTimer = 0;
        }
    }

    // 🔍 Поиск цели с увеличенной дальностью (до 6 блоков, как на сервере)
    private Entity findTarget(EntityPlayer player) {
        double reach = 6.0; // Серверная дальность атаки
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach + 1.0);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        Vec3d lookVec = player.getLookVec();
        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            // Расстояние от глаз игрока до хитбокса цели
            double distanceToHitbox = player.getDistance(entity) - (entity.width / 2.0);
            if (distanceToHitbox > reach) continue;

            // Проверка, что цель в поле зрения (угол ~90 градусов)
            Vec3d toEntity = new Vec3d(entity.posX - player.posX, entity.posY - player.posY, entity.posZ - player.posZ).normalize();
            if (lookVec.dotProduct(toEntity) < 0.7) continue;

            if (distanceToHitbox < bestDistance) {
                bestDistance = distanceToHitbox;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }

    // ⚔️ Прямая отправка пакета атаки (обходит ограничение PlayerControllerMP в 3 блока)
    private void performAttack(EntityPlayer player, Entity target) {
        // Свайп рукой для визуала
        player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        
        // Отправляем пакет атаки напрямую на сервер
        Minecraft.getMinecraft().getConnection().sendPacket(
            new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK)
        );
    }
}
