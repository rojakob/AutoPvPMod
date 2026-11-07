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

@Mod(modid = AutoPvPMod.MODID, name = AutoPvPMod.NAME, version = AutoPvPMod.VERSION)
public class AutoPvPMod {

    public static final String MODID = "autopvp";
    public static final String NAME = "Auto PvP";
    public static final String VERSION = "1.0";

    private static KeyBinding keyToggleMod;
    private static KeyBinding keyToggleCrit;

    private static boolean isModActive = false;
    private static boolean isCritActive = false;

    // Счетчик тиков для синхронизации атаки
    private static int attackTimer = 0;
    private static final int ATTACK_DELAY = 12; // 12 тиков = 100% заряда для меча (1.6 attack speed)

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Клавиша X для включения/выключения всего мода
        keyToggleMod = new KeyBinding("key.autopvp.toggle", Keyboard.KEY_X, "key.categories.combat");
        // Клавиша R для включения/выключения критических прыжков
        keyToggleCrit = new KeyBinding("key.autopvp.crit", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        ClientRegistry.registerKeyBinding(keyToggleCrit);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP Mod v1.0 загружен! X - вкл/выкл, R - критические прыжки.");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Обработка клавиши X (Главный переключатель)
        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            
            // Принудительное выключение критических прыжков при выключении мода
            if (!isModActive && isCritActive) {
                isCritActive = false;
                mc.player.sendMessage(new TextComponentString("§eКритические прыжки: §cАвтоматически выключены"));
            }
            // Сброс таймера при включении, чтобы начать с чистого листа
            attackTimer = 0; 
        }

        // Обработка клавиши R (Только если мод включен)
        if (keyToggleCrit.isPressed()) {
            if (!isModActive) {
                mc.player.sendMessage(new TextComponentString("§cСначала включите AutoPvP (Клавиша X)"));
                return;
            }
            isCritActive = !isCritActive;
            String msg = "§eКритические прыжки: " + (isCritActive ? "§aВключены" : "§cВыключены");
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Работаем только в конце тика и только на клиенте (для отправки пакетов атаки)
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // Увеличиваем таймер каждый тик
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }

        // Если таймер не достиг нужного значения, ничего не делаем
        if (attackTimer < ATTACK_DELAY) return;

        // Ищем цель: сущность, на которую смотрит игрок
        Entity target = Minecraft.getMinecraft().objectMouseOver != null 
                ? Minecraft.getMinecraft().objectMouseOver.entityHit 
                : null;

        // Проверяем, что цель существует, жива и находится в пределах досягаемости (3 блока)
        if (target instanceof EntityLivingBase && target.getDistance(player) <= 3.0F) {
            
            // Если включены критические прыжки и игрок на земле — прыгаем
            if (isCritActive && player.onGround && !player.isInWater() && !player.isOnLadder()) {
                player.jump();
            }

            // Наносим удар
            Minecraft.getMinecraft().playerController.attackEntity(player, target);
            
            // Сбрасываем таймер после успешной атаки
            attackTimer = 0;
        }
    }
}