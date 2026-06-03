package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dji.sampleV5.aircraft.databinding.ActivityPatrolPhotoDialogBinding
import dji.sampleV5.aircraft.models.PatrolPhotoVM

class PatrolPhotoDialogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatrolPhotoDialogBinding
    private lateinit var patrolPhotoVM: PatrolPhotoVM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatrolPhotoDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        patrolPhotoVM = ViewModelProvider(this)[PatrolPhotoVM::class.java]

        binding.btnConfirm.setOnClickListener {
            val id = binding.etPatrolId.text.toString().trim()
            patrolPhotoVM.captureAndSend(id)
        }

        patrolPhotoVM.statusMessage.observe(this) { msg ->
            binding.tvStatus.text = msg
        }

        patrolPhotoVM.isProcessing.observe(this) { processing ->
            binding.btnConfirm.isEnabled = !processing
            binding.progressBar.visibility = if (processing) View.VISIBLE else View.GONE
        }
    }
}
