package com.silauncer.cepat.home

import android.graphics.Canvas
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

/**
 * Utilitas over-scroll ringan dengan efek spring,
 * mengambil inspirasi dari responsivitas over-scroll modern tanpa membebani thread.
 */
object OverScroll {

    fun setup(recyclerView: RecyclerView) {
        recyclerView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
                return object : EdgeEffect(view.context) {
                    
                    override fun onPull(deltaDistance: Float) {
                        super.onPull(deltaDistance)
                        handlePull(deltaDistance)
                    }

                    override fun onPull(deltaDistance: Float, displacement: Float) {
                        super.onPull(deltaDistance, displacement)
                        handlePull(deltaDistance)
                    }

                    private fun handlePull(deltaDistance: Float) {
                        val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                        val translationYDelta = sign * view.height * deltaDistance * 0.2f
                        view.translationY += translationYDelta
                    }

                    override fun onRelease() {
                        super.onRelease()
                        if (view.translationY != 0f) {
                            val anim = SpringAnimation(view, SpringAnimation.TRANSLATION_Y, 0f)
                            anim.spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                            anim.spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                            anim.start()
                        }
                    }

                    override fun onAbsorb(velocity: Int) {
                        super.onAbsorb(velocity)
                        val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                        val translationVelocity = sign * velocity * 0.5f
                        val anim = SpringAnimation(view, SpringAnimation.TRANSLATION_Y, 0f)
                        anim.setStartVelocity(translationVelocity)
                        anim.spring.stiffness = SpringForce.STIFFNESS_MEDIUM
                        anim.spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                        anim.start()
                    }

                    override fun draw(canvas: Canvas?): Boolean {
                        return false // Jangan gambar efek glow biru bawaan
                    }
                }
            }
        }
    }
}
