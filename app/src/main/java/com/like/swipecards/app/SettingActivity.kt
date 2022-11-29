package com.like.swipecards.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.like.swipecards.SwipeCardsAdapterView
import com.like.swipecards.app.databinding.ActivitySettingBinding

class SettingActivity : AppCompatActivity() {
    private val mBinding by lazy {
        DataBindingUtil.setContentView<ActivitySettingBinding>(
            this,
            R.layout.activity_setting
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding.config = intent?.getParcelableExtra("config")
        mBinding.btnConfirm.setOnClickListener {
            Intent().apply {
                putExtra("config", getConfig())
                setResult(Activity.RESULT_OK, this)
                finish()
            }
        }
    }

    private fun getConfig(): SwipeCardsAdapterView.Config = SwipeCardsAdapterView.Config(
        maxChildCount = mBinding.etMaxChildCount.text.toString().toInt(),
        prefetchCount = mBinding.etPrefetchCount.text.toString().toInt(),
        yOffsetStep = mBinding.etYOffsetStep.text.toString().toInt(),
        scaleStep = mBinding.etScaleStep.text.toString().toFloat(),
        scaleMax = mBinding.etScaleMax.text.toString().toFloat(),
        animDuration = mBinding.etAnimDuration.text.toString().toLong(),
        maxRotationAngle = mBinding.etMaxRotationAngle.text.toString().toFloat(),
        borderPercent = mBinding.etBorderPercent.text.toString().toFloat(),
        isNeedSwipe = mBinding.etIsNeedSwipe.text.toString() == "true",
        maxUndoCacheSize = mBinding.etMaxUndoCacheSize.text.toString().toInt(),
    )

}
