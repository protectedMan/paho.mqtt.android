package info.mqtt.android.extsample.activity

import info.mqtt.android.extsample.internal.Connections.Companion.getInstance
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import info.mqtt.android.extsample.R
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import java.util.HashMap

import android.view.LayoutInflater
import com.google.android.material.tabs.TabLayout
import info.mqtt.android.extsample.utils.connect


class ConnectionFragment : Fragment() {
    private lateinit var tabLayout: TabLayout
    private var connection: Connection? = null
    private var connectSwitch: SwitchCompat? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val connections: HashMap<String, Connection> = getInstance(requireActivity()).connections
        connection = connections[requireArguments().getString(ActivityConstants.CONNECTION_KEY)]
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_connection, container, false)

        tabLayout = rootView.findViewById(R.id.tablayout)
        tabLayout.addTab(tabLayout.newTab().setText("History").setId(0))
        tabLayout.addTab(tabLayout.newTab().setText("Messages").setId(1))
        tabLayout.addTab(tabLayout.newTab().setText("Publish").setId(2))
        tabLayout.addTab(tabLayout.newTab().setText("Subscribe").setId(3))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.id) {
                    0 -> displayFragment(HistoryFragment())
                    1 -> displayFragment(MessagesFragment())
                    2 -> displayFragment(PublishFragment())
                    3 -> displayFragment(SubscriptionFragment())
                }
            }

        })

        return rootView
    }

    private fun changeConnectedState(state: Boolean) {
        tabLayout.getTabAt(1)?.view?.isClickable = state
        tabLayout.getTabAt(2)?.view?.isClickable = state
        connectSwitch?.isChecked = state
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_connection, menu)
        connectSwitch = menu.findItem(R.id.connect_switch).actionView.findViewById(R.id.switchForActionBar)
        connectSwitch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                connection?.connect(requireActivity())
                changeConnectedState(true)
            } else {
                Handler(Looper.getMainLooper()).postDelayed(
                    { tabLayout.getTabAt(0)?.select() }, 100
                )
                connection?.client?.disconnect()
                changeConnectedState(false)
            }
        }
        changeConnectedState(connection!!.isConnected)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun displayFragment(fragment: Fragment) {
        val bundle = Bundle()
        bundle.putString(ActivityConstants.CONNECTION_KEY, connection!!.handle())
        fragment.arguments = bundle
        val fragmentTransaction = parentFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.tabcontent, fragment)
        fragmentTransaction.commit()
    }
}
