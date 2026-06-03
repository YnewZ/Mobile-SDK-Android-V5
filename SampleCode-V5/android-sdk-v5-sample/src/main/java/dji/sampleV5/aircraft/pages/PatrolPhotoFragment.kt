package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragPatrolPhotoBinding
import dji.sampleV5.aircraft.models.PatrolPhotoVM

class PatrolPhotoFragment : DJIFragment() {

    private val patrolPhotoVM: PatrolPhotoVM by viewModels()
    private var _binding: FragPatrolPhotoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragPatrolPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConfirm.setOnClickListener {
            val id = binding.etPatrolId.text.toString().trim()
            patrolPhotoVM.captureAndSend(id)
        }

        patrolPhotoVM.statusMessage.observe(viewLifecycleOwner) { msg ->
            binding.tvStatus.text = msg
        }

        patrolPhotoVM.isProcessing.observe(viewLifecycleOwner) { processing ->
            binding.btnConfirm.isEnabled = !processing
            binding.progressBar.visibility = if (processing) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
