package info.mqtt.android.extsample.activity

import android.content.Intent
import info.mqtt.android.extsample.internal.Connections.Companion.getInstance
import info.mqtt.android.extsample.activity.Connection.Companion.createConnection
import info.mqtt.android.extsample.activity.FragmentDrawer.FragmentDrawerListener
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import info.mqtt.android.extsample.R
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import info.hannes.logcat.BothLogActivity
import info.mqtt.android.extsample.model.ConnectionModel
import timber.log.Timber
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.ArrayList

class MainActivity : AppCompatActivity(), FragmentDrawerListener {

    private val changeListener: ChangeListener = ChangeListener()
    private val mainActivity = this
    private var drawerFragment: FragmentDrawer? = null
    private var connectionMap: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mToolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(mToolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        drawerFragment = supportFragmentManager.findFragmentById(R.id.fragment_navigation_drawer) as FragmentDrawer?
        drawerFragment!!.setUp(R.id.fragment_navigation_drawer, findViewById<View>(R.id.drawer_layout) as DrawerLayout, mToolbar)
        drawerFragment!!.setDrawerListener(this)
        populateConnectionList()
    }

    fun removeConnectionRow(connection: Connection?) {
        drawerFragment!!.removeConnection(connection!!)
        populateConnectionList()
    }

    private fun populateConnectionList() {
        drawerFragment!!.clearConnections()

        // get all the available connections
        val connections: Map<String, Connection> = getInstance(this).connections
        connectionMap = ArrayList()
        val connectionIterator: Iterator<*> = connections.entries.iterator()
        while (connectionIterator.hasNext()) {
            val pair = connectionIterator.next() as Map.Entry<*, *>
            drawerFragment!!.addConnection((pair.value as Connection?)!!)
            connectionMap!!.add(pair.key as String)
        }
        if (connectionMap!!.size == 0) {
            displayView(-1)
        } else {
            displayView(0)
        }
    }

    override fun onDrawerItemSelected(position: Int) {
        displayView(position)
    }

    override fun onDrawerItemLongSelected(position: Int) {
        displayDeleteView(position)
    }

    override fun onAddConnectionSelected() {
        val editConnectionFragment: Fragment = EditConnectionFragment()
        val title = "Edit Connection"
        displayFragment(editConnectionFragment, title)
    }

    private fun displayDeleteView(position: Int) {
        if (position == -1) {
            displayFragment(HomeFragment(), "Home")
        } else {
            val fragment: Fragment = ManageConnectionFragment()
            val bundle = Bundle()
            bundle.putString(ActivityConstants.CONNECTION_KEY, connectionMap!![position])
            fragment.arguments = bundle
            val connections: Map<String, Connection> = getInstance(this)
                .connections
            val connection = connections[connectionMap!![position]]
            displayFragment(fragment, connection.toString())
        }
    }

    private fun displayView(position: Int) {
        if (position == -1) {
            displayFragment(HomeFragment(), "Home")
        } else {
            val fragment: Fragment = ConnectionFragment()
            val bundle = Bundle()
            bundle.putString(ActivityConstants.CONNECTION_KEY, connectionMap!![position])
            fragment.arguments = bundle
            val connections: Map<String, Connection> = getInstance(this).connections
            val connection = connections[connectionMap!![position]]
            val title = connection!!.id
            displayFragment(fragment, title)
        }
    }

    private fun displayFragment(fragment: Fragment, title: String) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.container_body, fragment)
        fragmentTransaction.commit()

        supportActionBar?.title = title
    }

    fun updateAndConnect(model: ConnectionModel) {
        val connections: Map<String, Connection> = getInstance(this).connections
        Timber.i("Updating connection: ${connections.keys}")
        val connection = connections[model.clientHandle]
        // First disconnect the current instance of this connection
        if (connection!!.isConnected) {
            connection.changeConnectionStatus(Connection.ConnectionStatus.DISCONNECTING)
            connection.client.disconnect()
        }
        // Update the connection.
        connection.updateConnection(model.clientId, model.serverHostName, model.serverPort, model.isTlsConnection)
        connection.changeConnectionStatus(Connection.ConnectionStatus.CONNECTING)
        val actionArgs = arrayOfNulls<String>(1)
        actionArgs[0] = model.clientId
        val callback = ActionListener(this, Action.CONNECT, connection, *actionArgs)
        connection.client.setCallback(MqttCallbackHandler(this, model.clientHandle))
        connection.client.setTraceCallback(MqttTraceCallback())
        val connOpts = optionsFromModel(model)
        connection.addConnectionOptions(connOpts)
        getInstance(this).updateConnection(connection)
        drawerFragment!!.updateConnection(connection)
        connection.client.connect(connOpts, null, callback)
        val fragment: Fragment = ConnectionFragment()
        val bundle = Bundle()
        bundle.putString(ActivityConstants.CONNECTION_KEY, connection.handle())
        fragment.arguments = bundle
        val title = connection.id
        displayFragment(fragment, title)
    }

    /**
     * Takes a [ConnectionModel] and uses it to connect
     * and then persist.
     *
     * @param model - The connection Model
     */
    fun persistAndConnect(model: ConnectionModel) {
        Timber.i("Persisting new connection:${model.clientHandle}")
        val connection = createConnection(model.clientHandle, model.clientId, model.serverHostName, model.serverPort, this, model.isTlsConnection)
        connection.registerChangeListener(changeListener)
        connection.changeConnectionStatus(Connection.ConnectionStatus.CONNECTING)
        val actionArgs = arrayOfNulls<String>(1)
        actionArgs[0] = model.clientId
        val callback = ActionListener(this, Action.CONNECT, connection, *actionArgs)
        connection.client.setCallback(MqttCallbackHandler(this, model.clientHandle))
        connection.client.setTraceCallback(MqttTraceCallback())
        val connOpts = optionsFromModel(model)
        connection.addConnectionOptions(connOpts)
        getInstance(this).addConnection(connection)
        connectionMap!!.add(model.clientHandle)
        drawerFragment!!.addConnection(connection)
        connection.client.connect(connOpts, null, callback)
        val connectFragment: Fragment = ConnectionFragment()
        val bundle = Bundle()
        bundle.putString(ActivityConstants.CONNECTION_KEY, connection.handle())
        bundle.putBoolean(ActivityConstants.CONNECTED, true)
        connectFragment.arguments = bundle
        val title = connection.id
        displayFragment(connectFragment, title)
    }

    private fun optionsFromModel(model: ConnectionModel): MqttConnectOptions {
        val connOpts = MqttConnectOptions()
        connOpts.isCleanSession = model.isCleanSession
        connOpts.connectionTimeout = model.timeout
        connOpts.keepAliveInterval = model.keepAlive
        if (model.username != ActivityConstants.empty) {
            connOpts.userName = model.username
        }
        if (model.password != ActivityConstants.empty) {
            connOpts.password = model.password.toCharArray()
        }
        if (model.lwtTopic != ActivityConstants.empty && model.lwtMessage != ActivityConstants.empty) {
            connOpts.setWill(model.lwtTopic, model.lwtMessage.toByteArray(), model.lwtQos, model.isLwtRetain)
        }
        //   if (tlsConnection){
        //       // TODO Add Keys to conOpts here
        //       //connOpts.setSocketFactory();
        //   }
        return connOpts
    }

    fun publish(connection: Connection, topic: String?, message: String, qos: Int, retain: Boolean) {
        val actionArgs = arrayOfNulls<String>(2)
        actionArgs[0] = message
        actionArgs[1] = topic
        val callback = ActionListener(this, Action.PUBLISH, connection, *actionArgs)
        connection.client.publish(topic!!, message.toByteArray(), qos, retain, null, callback)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuLogcat -> {
                startActivity(Intent(this, BothLogActivity::class.java))
            }
        }
        return false
    }

    /**
     * This class ensures that the user interface is updated as the Connection objects change their states
     */
    private inner class ChangeListener : PropertyChangeListener {
        override fun propertyChange(event: PropertyChangeEvent) {
            if (event.propertyName != ActivityConstants.ConnectionStatusProperty) {
                return
            }
            mainActivity.runOnUiThread { mainActivity.drawerFragment!!.notifyDataSetChanged() }
        }
    }
}
