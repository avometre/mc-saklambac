package com.saklambac;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ObjectHolder;
import net.minecraft.util.SoundEvent;

@Mod("saklambac")
public class SaklambacMod {
    public static Item FLUT;
    public static Item SAKSOFON;

    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new net.minecraft.util.ResourceLocation("saklambac", "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    public SaklambacMod() {
        System.out.println("Saklambaç Modu Yüklendi!");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        SaklambacMod.CHANNEL.registerMessage(
            0,
            com.saklambac.SaklambacBaslatCommand.SnowballRefillPacket.class,
            com.saklambac.SaklambacBaslatCommand.SnowballRefillPacket::encode,
            com.saklambac.SaklambacBaslatCommand.SnowballRefillPacket::decode,
            com.saklambac.SaklambacBaslatCommand.SnowballRefillPacket::handle
        );
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModItemRegistry {
        @SubscribeEvent
        public static void onRegisterItems(final RegistryEvent.Register<Item> event) {
            FLUT = new Item(new Item.Properties().tab(ItemGroup.TAB_MISC).stacksTo(1));
            FLUT.setRegistryName("saklambac", "flut");
            event.getRegistry().register(FLUT);
            SAKSOFON = new Item(new Item.Properties().tab(ItemGroup.TAB_MISC).stacksTo(1));
            SAKSOFON.setRegistryName("saklambac", "saksofon");
            event.getRegistry().register(SAKSOFON);
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModSoundRegistry {
        @SubscribeEvent
        public static void onRegisterSounds(final net.minecraftforge.event.RegistryEvent.Register<SoundEvent> event) {
            event.getRegistry().registerAll(
                new SoundEvent(new net.minecraft.util.ResourceLocation("saklambac", "flut_1")).setRegistryName("saklambac", "flut_1"),
                new SoundEvent(new net.minecraft.util.ResourceLocation("saklambac", "flut_2")).setRegistryName("saklambac", "flut_2"),
                new SoundEvent(new net.minecraft.util.ResourceLocation("saklambac", "flut_3")).setRegistryName("saklambac", "flut_3"),
                new SoundEvent(new net.minecraft.util.ResourceLocation("saklambac", "saksofon_1")).setRegistryName("saklambac", "saksofon_1"),
                new SoundEvent(new net.minecraft.util.ResourceLocation("saklambac", "saksofon_2")).setRegistryName("saklambac", "saksofon_2"),
                new SoundEvent(new net.minecraft.util.ResourceLocation("saklambac", "saksofon_3")).setRegistryName("saklambac", "saksofon_3")
            );
        }
    }
} 