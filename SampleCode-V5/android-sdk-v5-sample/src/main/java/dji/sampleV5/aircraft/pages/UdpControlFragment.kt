package dji.sampleV5.aircraft.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.databinding.FragUdpControlPageBinding
import dji.sampleV5.aircraft.models.UdpControlVM
import dji.v5.utils.common.LogUtils

class UdpControlFragment : DJIFragment() {

    companion object {
        const val TAG = "UdpControlFragment"
    }

    private var binding: FragUdpControlPageBinding? = null
    private val udpControlVM: UdpControlVM by viewModels()
    private var messageList = ArrayList<String>()
    private lateinit var messageAdapter: ArrayAdapter<String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragUdpControlPageBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
        initListener()
    }

    private fun initView() {
        messageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, messageList)
        binding?.messageListview?.adapter = messageAdapter

        udpControlVM.serverStatusLiveData.observe(viewLifecycleOwner) { status ->
            binding?.tvServerStatus?.text = status
            binding?.btnToggleServer?.text = if (udpControlVM.isServerRunning()) "停止" else "启动"
        }

        udpControlVM.receiveMessageLiveData.observe(viewLifecycleOwner) { msg ->
            LogUtils.i(TAG, msg)
            messageList.add(msg)
            messageAdapter.notifyDataSetChanged()
            binding?.messageListview?.setSelection(messageList.size - 1)
        }
    }

    private fun initListener() {
        binding?.btnToggleServer?.setOnClickListener {
            if (udpControlVM.isServerRunning()) {
                udpControlVM.stopUdpServer()
            } else {
                udpControlVM.startUdpServer()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
