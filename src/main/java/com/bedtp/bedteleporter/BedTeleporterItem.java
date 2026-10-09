package com.bedtp.bedteleporter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

public class BedTeleporterItem extends Item {

    public BedTeleporterItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack); // client: just plays the swing animation
        }

        // 1. Does the player have a respawn point?
        BlockPos bedPos = sp.getRespawnPosition();
        if (bedPos == null) {
            sp.displayClientMessage(Component.literal("You don't have a bed spawn set."), true);
            return InteractionResultHolder.fail(stack);
        }

        // 2. Get the level it's in (could be another dimension)
        ServerLevel target = sp.server.getLevel(sp.getRespawnDimension());
        if (target == null) return InteractionResultHolder.fail(stack);

        // 3. Is the bed still there, and is there a safe spot to stand?
        BlockState state = target.getBlockState(bedPos);
        if (!(state.getBlock() instanceof BedBlock)) {
            sp.displayClientMessage(Component.literal("Your bed is missing."), true);
            return InteractionResultHolder.fail(stack);
        }

        Optional<Vec3> stand = BedBlock.findStandUpPosition(
                EntityType.PLAYER, target, bedPos, state.getValue(BedBlock.FACING), sp.getYRot());
        if (stand.isEmpty()) {
            sp.displayClientMessage(Component.literal("Your bed is obstructed."), true);
            return InteractionResultHolder.fail(stack);
        }

        // 4. Departure effects
        ServerLevel from = sp.serverLevel();
        from.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1f);
        from.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.3, 0.5, 0.3, 0.02);

        // 5. Teleport
        Vec3 dest = stand.get();
        sp.teleportTo(dest.x, dest.y, dest.z);

        // 6. Cancel the fall so it saves you from ravines
        sp.resetFallDistance();
        sp.setDeltaMovement(Vec3.ZERO);
        sp.hurtMarked = true;

        // 7. Arrival effects
        target.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.3f);
        target.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1, dest.z, 30, 0.3, 0.5, 0.3, 0.02);
        sp.displayClientMessage(Component.literal("Teleported to your bed."), true);

        return InteractionResultHolder.success(stack);
    }
}
