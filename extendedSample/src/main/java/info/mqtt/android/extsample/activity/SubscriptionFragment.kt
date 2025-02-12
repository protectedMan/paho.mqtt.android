package info.mqtt.android.extsample.activity

import info.mqtt.android.extsample.internal.Connections.Companion.getInstance
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import info.mqtt.android.extsample.R
import info.mqtt.android.extsample.components.SubscriptionListItemAdapter
import timber.log.Timber
import org.eclipse.paho.client.mqttv3.MqttException
import android.widget.AdapterView.OnItemSelectedListener
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import info.mqtt.android.extsample.databinding.FragmentSubscriptionsBinding
import info.mqtt.android.extsample.databinding.SubscriptionDialogBinding
import info.mqtt.android.extsample.model.Subscription
import java.util.HashMap

class SubscriptionFragment : Fragment() {

    private var tempQosValue = 0
    private lateinit var connection: Connection

    private var _binding: FragmentSubscriptionsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bundle = this.arguments
        val connectionHandle = bundle!!.getString(ActivityConstants.CONNECTION_KEY)
        val connections: HashMap<String, Connection> = getInstance(requireActivity()).connections
        connection = connections[connectionHandle]!!
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionsBinding.inflate(inflater, container, false)
        val rootView = binding.root
        binding.subscribeButton.setOnClickListener { showInputDialog() }
        binding.subscriptionListView.adapter = SubscriptionListItemAdapter(requireContext(), connection)
        return rootView
    }

    private fun showInputDialog() {
        val dialogBinding = SubscriptionDialogBinding
            .inflate(LayoutInflater.from(context))

        val adapter = ArrayAdapter.createFromResource(requireActivity(), R.array.qos_options, android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.subscriptionQosSpinner.adapter = adapter
        dialogBinding.subscriptionQosSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                tempQosValue = resources.getStringArray(R.array.qos_options)[position].toInt()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val alertDialogBuilder = AlertDialog.Builder(requireActivity())
        alertDialogBuilder.setView(dialogBinding.root)
        alertDialogBuilder.setCancelable(true).setPositiveButton(R.string.subscribe_ok) { _, _ ->
            val topic = dialogBinding.subscriptionTopicEditText.text.toString()
            val subscription = Subscription(topic, tempQosValue, connection.handle(), dialogBinding.showNotificationsSwitch.isChecked)
            try {
                connection.addNewSubscription(subscription)
                (binding.subscriptionListView.adapter as SubscriptionListItemAdapter).refresh()
            } catch (ex: MqttException) {
                Timber.d(ex)
            }
            adapter.notifyDataSetChanged()
        }.setNegativeButton(R.string.subscribe_cancel) { dialog, _ -> dialog.cancel() }
        val alert = alertDialogBuilder.create()
        alert.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        alert.show()
    }
}