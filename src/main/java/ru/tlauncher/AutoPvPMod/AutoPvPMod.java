package ru.tlauncher.safewalker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
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

    public static final String MODID = "autopvp18"; // Изменено на autopvp18
    public static final String NAME = "Auto PvP 1.8";
    public static final String VERSION = "1.8"; // Поднял версию для ясности

    private static KeyBinding keyToggleMod;
    private static boolean isModActive = false;
    private static int attackTimer = 0;
    private static final int ATTACK_DELAY = 4; // 4 тика = 5 CPS (идеально для 1.8 PvP)

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Изменили клавишу на Z
        keyToggleMod = new KeyBinding("key.autopvp18.toggle", Keyboard.KEY_R, "key.categories.combat");
        
        ClientRegistry.registerKeyBinding(keyToggleMod);
        
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("AutoPvP 1.8 Mod загружен! Клавиша Z для активации.");
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Обработка клавиши Z (Вкл/Выкл мод)
        if (keyToggleMod.isPressed()) {
            isModActive = !isModActive;
            String msg = "AutoPvP 1.8: " + (isModActive ? "§aВключен" : "§cВыключен");
            mc.player.sendMessage(new TextComponentString(msg));
            
            attackTimer = 0; // Сбрасываем таймер при переключении
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Работаем только на клиенте и в конце тика
        if (event.phase != TickEvent.Phase.END || !event.player.world.isRemote) return;
        
        EntityPlayer player = event.player;
        if (player != Minecraft.getMinecraft().player || !isModActive) return;

        // Накапливаем таймер атаки
        if (attackTimer < ATTACK_DELAY) {
            attackTimer++;
        }
        if (attackTimer < ATTACK_DELAY) return; // Ждем 4 тика

        // Ищем цель
        Entity target = findTarget(player);
        if (target == null) return;

        // Наносим удар
        Minecraft.getMinecraft().playerController.attackEntity(player, target);
        attackTimer = 0; // Сбрасываем таймер после успешного удара
    }

    // 🔍 Поиск цели (честные 3 блока для выживания)
    private Entity findTarget(EntityPlayer player) {
        double reach = 3.0; 
        AxisAlignedBB area = player.getEntityBoundingBox().grow(reach + 1.0);
        List<EntityLivingBase> entities = player.world.getEntitiesWithinAABB(EntityLivingBase.class, area, e -> e != player && e.isEntityAlive());
        
        Vec3d lookVec = player.getLookVec();
        Entity bestTarget = null;
        double bestDistance = Double.MAX_VALUE;

        for (EntityLivingBase entity : entities) {
            // Расстояние до хитбокса (учитываем ширину модели)
            double distanceToHitbox = player.getDistance(entity) - (entity.width / 2.0);
            if (distanceToHitbox > reach) continue;

            // Проверка поля зрения (угол обзора)
            Vec3d toEntity = new Vec3d(entity.posX - player.posX, entity.posY - player.posY, entity.posZ - player.posZ).normalize();
            double dot = lookVec.dotProduct(toEntity);
            if (dot < 0.7) continue; 

            if (distanceToHitbox < bestDistance) {
                bestDistance = distanceToHitbox;
                bestTarget = entity;
            }
        }
        return bestTarget;
    }
}
