package com.arezoonazer.player.view

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.arezoonazer.player.R
import com.arezoonazer.player.argument.PlayerParams
import com.arezoonazer.player.databinding.ActivityPlayerBinding
import com.arezoonazer.player.databinding.ExoPlayerViewBinding
import com.arezoonazer.player.di.AssistedFactory
import com.arezoonazer.player.extension.gone
import com.arezoonazer.player.extension.hideSystemUI
import com.arezoonazer.player.extension.resolveSystemGestureConflict
import com.arezoonazer.player.extension.setImageButtonTintColor
import com.arezoonazer.player.extension.visible
import com.arezoonazer.player.util.CustomPlaybackState
import com.arezoonazer.player.util.track.TrackEntity
import com.arezoonazer.player.view.track.TrackSelectionDialog
import com.arezoonazer.player.viewmodel.PlayerViewModel
import com.arezoonazer.player.viewmodel.QualityViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    private val binding: ActivityPlayerBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityPlayerBinding.inflate(layoutInflater)
    }

    private val exoBinding: ExoPlayerViewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ExoPlayerViewBinding.bind(binding.root)
    }

    private val playerParams: PlayerParams by lazy(LazyThreadSafetyMode.NONE) {
        intent.getSerializableExtra(PLAYER_PARAMS_EXTRA) as PlayerParams
    }

    @Inject
    lateinit var playerViewModelFactory: AssistedFactory

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModel.provideFactory(playerViewModelFactory, playerParams)
    }

    private val qualityViewModel: QualityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        viewModel.onActivityCreate(this)
        qualityViewModel.onActivityCreated()
        resolveSystemGestureConflict()
        initClickListeners()

        with(viewModel) {
            playerLiveData.observe(this@PlayerActivity) { exoPlayer ->
                binding.exoPlayerView.player = exoPlayer
            }

            playbackStateLiveData.observe(this@PlayerActivity) { playbackState ->
                setProgressbarVisibility(playbackState)
                setVideoControllerVisibility(playbackState)
            }

            isMuteLiveData.observe(
                this@PlayerActivity,
                exoBinding.exoControllerPlaceholder.muteButton::setMuteState
            )
        }

        with(qualityViewModel) {
            qualityEntitiesLiveData.observe(this@PlayerActivity, ::setupQualityButtons)
            onQualitySelectedLiveData.observe(this@PlayerActivity) {
                dismissTrackSelectionDialogIfExist()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI(binding.root)
    }

    private fun initClickListeners() {
        with(exoBinding.exoControllerPlaceholder) {
            exoBackButton.setOnClickListener { onBackPressed() }
            playPauseButton.setOnClickListener { viewModel.onPlayButtonClicked() }
            muteButton.setOnClickListener { viewModel.onMuteClicked() }
            replayButton.setOnClickListener { viewModel.onReplayClicked() }
        }
    }

    private fun setProgressbarVisibility(playbackState: CustomPlaybackState) {
        binding.progressBar.isVisible = playbackState == CustomPlaybackState.LOADING
    }

    private fun setVideoControllerVisibility(playbackState: CustomPlaybackState) {
        exoBinding.exoControllerPlaceholder.run {
            playPauseButton.setState(playbackState)
            when (playbackState) {
                CustomPlaybackState.PLAYING,
                CustomPlaybackState.PAUSED -> {
                    root.visible()
                    replayButton.gone()
                }
                CustomPlaybackState.ERROR,
                CustomPlaybackState.ENDED -> {
                    replayButton.visible()
                }
                else -> {
                    replayButton.gone()
                }
            }
        }
    }

    private fun setupQualityButtons(qualities: List<TrackEntity>) {
        exoBinding.exoControllerPlaceholder.qualityButton.apply {
            if (qualities.isNotEmpty()) {
                setImageButtonTintColor(R.color.white)
                setOnClickListener { openTrackSelectionDialog(qualities) }
            }
        }
    }

    private fun openTrackSelectionDialog(items: List<TrackEntity>) {
        dismissTrackSelectionDialogIfExist()
        TrackSelectionDialog.newInstance(items).show(supportFragmentManager, DIALOG_TAG)
    }

    private fun dismissTrackSelectionDialogIfExist() {
        with(supportFragmentManager) {
            val previousDialog = supportFragmentManager.findFragmentByTag(DIALOG_TAG)
            if (previousDialog != null) {
                (previousDialog as TrackSelectionDialog).dismiss()
                beginTransaction().remove(previousDialog)
            }
        }
    }

    companion object {
        const val PLAYER_PARAMS_EXTRA = "playerParamsExtra"
        private const val DIALOG_TAG = "dialogTag"
    }
}