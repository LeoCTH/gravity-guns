package com.leocth.gravityguns.item

import com.leocth.gravityguns.GravityGuns
import com.leocth.gravityguns.physics.GrabUtil
import com.leocth.gravityguns.physics.GrabbingManager
import com.leocth.gravityguns.util.ext.setAnimation
import com.leocth.gravityguns.util.ext.toBullet
import dev.lazurite.rayon.core.impl.bullet.thread.PhysicsThread
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import software.bernie.geckolib3.core.AnimationState
import software.bernie.geckolib3.core.IAnimatable
import software.bernie.geckolib3.core.PlayState
import software.bernie.geckolib3.core.controller.AnimationController
import software.bernie.geckolib3.core.manager.AnimationData
import software.bernie.geckolib3.core.manager.AnimationFactory
import software.bernie.geckolib3.network.GeckoLibNetwork
import software.bernie.geckolib3.network.ISyncable
import software.bernie.geckolib3.util.GeckoLibUtil

class GravityGunItem(settings: Settings) : Item(settings), IAnimatable, ISyncable {
    private val factory = AnimationFactory(this)

    init {
        GeckoLibNetwork.registerSyncable(this)
    }

    companion object {
        private const val CONTROLLER_ID = "controller"
        private const val EXTEND = 0
        private const val RETRACT = 1

        var ItemStack.power: Double
            get() = tag?.getDouble("power") ?: 0.0
            set(value) { orCreateTag.putDouble("power", value) }
    }

    override fun getMaxUseTime(stack: ItemStack): Int = 20000

    override fun onStoppedUsing(stack: ItemStack, world: World, user: LivingEntity, remainingUseTicks: Int) {
        if (!world.isClient) {
            val grabbingManager = GrabbingManager.SERVER

            if (user is ServerPlayerEntity && grabbingManager.isPlayerGrabbing(user)) {
                val instance = grabbingManager.instances[user.uuid]
                instance?.let {
                    val config = GravityGuns.CONFIG
                    val velocity = user.rotationVector.multiply(config.launchInitialVelocityMultiplier).toBullet()
                    PhysicsThread.get(world).execute {
                        it.grabbedBody.setLinearVelocity(velocity)
                    }
                }

                grabbingManager.tryUngrab(user, 5.0f)
                syncAnimation(user, world, stack, RETRACT)
            }
        }
    }

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)

        if (!world.isClient) {
            val grabbingManager = GrabbingManager.SERVER

            if (!grabbingManager.isPlayerGrabbing(user)) {
                val config = GravityGuns.CONFIG

                val thingToGrab =
                    GrabUtil.getEntityToGrab(user, config.entityReachDistance) ?:
                    GrabUtil.getBlockToGrab(user, config.blockReachDistance, stack.power) ?:
                    return TypedActionResult.fail(stack)

                grabbingManager.tryGrab(user, thingToGrab)
                user.setCurrentHand(hand)
                syncAnimation(user, world, stack, EXTEND)

                return TypedActionResult.consume(stack)
            }
        }
        return super.use(world, user, hand)
    }

    override fun registerControllers(data: AnimationData) {
        val controller = AnimationController(this, CONTROLLER_ID, 0f) {
            PlayState.CONTINUE
        }

        data.addAnimationController(controller)
    }

    override fun getFactory(): AnimationFactory = factory

    fun syncAnimation(user: PlayerEntity, world: World, stack: ItemStack, anim: Int) {
        if (world !is ServerWorld) throw IllegalStateException("syncAnimation may *only* be called on the logical server")

        val id = GeckoLibUtil.guaranteeIDForStack(stack, world)
        GeckoLibNetwork.syncAnimation(user, this, id, anim)
        for (otherPlayer in PlayerLookup.tracking(user)) {
            GeckoLibNetwork.syncAnimation(otherPlayer, this, id, anim)
        }
    }

    override fun onAnimationSync(id: Int, state: Int) {
        val controller = GeckoLibUtil.getControllerForID(factory, id, CONTROLLER_ID)
        controller.markNeedsReload()
        controller.setAnimation {
            when (state) {
                EXTEND -> it
                    .addAnimation("animation.gravity_gun.extend")
                    .addAnimation("animation.gravity_gun.open")
                RETRACT -> it
                    .addAnimation("animation.gravity_gun.retract")
                    .addAnimation("animation.gravity_gun.closed")
            }
        }
    }
}