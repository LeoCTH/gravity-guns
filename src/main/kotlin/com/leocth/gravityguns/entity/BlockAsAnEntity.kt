package com.leocth.gravityguns.entity

import com.jme3.math.Vector3f
import com.leocth.gravityguns.physics.GrabbingManager
import com.leocth.gravityguns.util.ext.*
import dev.lazurite.rayon.core.impl.bullet.collision.body.BlockRigidBody
import dev.lazurite.rayon.core.impl.bullet.collision.space.MinecraftSpace
import dev.lazurite.rayon.entity.api.EntityPhysicsElement
import dev.lazurite.rayon.entity.impl.collision.body.EntityRigidBody
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.fluid.Fluids
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.Packet
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

class BlockAsAnEntity(
    world: World,
) : Entity(TYPE, world), EntityPhysicsElement {
    private val rigidBody = EntityRigidBody(this)

    var states: CompactBlockStates
        get() = dataTracker.get(BLOCK_STATES)
        private set(value) { dataTracker.set(BLOCK_STATES, value) }

    init {
        inanimate = true
        rigidBody.dragCoefficient = 0.0005f
        rigidBody.mass = 5.0f
    }

    constructor(
        world: World,
        pos: Vec3d,
        states: CompactBlockStates
    ): this(world) {
        setPosition(pos.x, pos.y + (1f - height) / 2.0, pos.z)
        this.states = states
    }

    override fun initDataTracker() {
        dataTracker.startTracking(BLOCK_STATES, CompactBlockStates.makeEmpty())
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {}
    override fun writeCustomDataToNbt(nbt: NbtCompound) {}

    override fun createSpawnPacket(): Packet<*>
        = EntitySpawnS2CPacket(this)

    override fun step(space: MinecraftSpace) {
        rigidBody.applyCentralForce(Vector3f(0f, 9f, 0f))
    }

    override fun getRigidBody() = rigidBody

    fun onBlockCollision(blockRigidBody: BlockRigidBody, impulse: Float) {
        // TODO
        /*
        if (world.isClient || isRemoved) return
        if (GrabbingManager.SERVER.isEntityBeingGrabbed(this)) return // don't settle if it's still being grabbed

        val physPos = BlockPos(rigidBody.getPhysicsLocation(null).toVec3d())
        val down = physPos.down()

        val hasSpace =
            !world.getBlockState(down).isAir &&
            world.getFluidState(down).fluid == Fluids.EMPTY

        if (hasSpace) {
            rigidBody.setDoTerrainLoading(false)
            kill()
            world.breakBlock(physPos, true)
            world.setBlockState(physPos, state)
        }
         */
    }

    companion object {
        private val BLOCK_STATES = DataTracker.registerData(BlockAsAnEntity::class.java, CompactBlockStates.DATA_HANDLER)

        val TYPE: EntityType<BlockAsAnEntity> = FabricEntityTypeBuilder.create<BlockAsAnEntity>()
            .dimensions(EntityDimensions.fixed(1f, 1f))
            .entityFactory { _: EntityType<BlockAsAnEntity>, world -> BlockAsAnEntity(world) }
            .build()
    }
}