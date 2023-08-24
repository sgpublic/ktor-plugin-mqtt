package io.traxter.ktor.mqtt

import io.ktor.server.application.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.ktor.events.EventDefinition
import org.eclipse.paho.mqttv5.client.IMqttAsyncClient
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttClientPersistence
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import org.eclipse.paho.mqttv5.client.MqttAsyncClient as PahoMqttClient

fun Application.Mqtt(config: Mqtt.Configuration.() -> Unit): Mqtt = install(Mqtt, config)

interface Mqtt : CoroutineScope, IMqttAsyncClient {

    class Configuration {
        var connectionOptions = MqttConnectionOptions()
        var persistence: MqttClientPersistence = MemoryPersistence()
        var broker: String = "tcp://localhost:1883"
        var clientId = "ktor_mqtt_client"
        var autoConnect: Boolean = false
        var initialSubscriptions = arrayOf<TopicSubscription>()

        fun initialSubscriptions(vararg subscription: TopicSubscription) {
            initialSubscriptions = initialSubscriptions.plus(subscription)
        }

        fun connectionOptions(configure: MqttConnectionOptions.() -> Unit) {
            connectionOptions.apply(configure)
        }
    }

    fun shutdown()
    fun addTopicListener(topic: Topic, listener: MessageListener)

    companion object Plugin : BaseApplicationPlugin<Application, Configuration, Mqtt> {
        override val key: AttributeKey<Mqtt> = AttributeKey("Mqtt")
        val ConnectedEvent: EventDefinition<Mqtt> = EventDefinition()
        val ClosedEvent: EventDefinition<Unit> = EventDefinition()
        lateinit var client: MqttClientPlugin

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): Mqtt {
            val applicationMonitor = pipeline.environment.monitor

            val config = Configuration().apply(configure)
            val logger = pipeline.log
            val delegate: IMqttAsyncClient = PahoMqttClient(config.broker, config.clientId, config.persistence)
            client = MqttClientPlugin(config, logger, delegate)

            if (config.autoConnect) client.connectToBroker().also { applicationMonitor.raise(ConnectedEvent, client) }

            applicationMonitor.subscribe(ApplicationStopPreparing) {
                client.shutdown()
                it.monitor.raise(ClosedEvent, Unit)
            }

            return client
        }
    }
}

class MqttClientPlugin(
    private val mConfig: Mqtt.Configuration,
    private val mLogger: Logger,
    mDelegate: IMqttAsyncClient
) : Mqtt, IMqttAsyncClient by mDelegate {

    private val parent: CompletableJob = Job()

    override val coroutineContext: CoroutineContext
        get() = parent

    private var messageListenerByTopic = ConcurrentHashMap<Topic, MessageListener>()

    override fun shutdown() {
        mLogger.info("shutting down Mqtt")
        parent.complete()
        messageListenerByTopic.clear()
        close()
        disconnectForcibly()
    }

    override fun addTopicListener(topic: Topic, listener: MessageListener) {
        messageListenerByTopic[topic] = listener
    }

    suspend fun unsubscribeFrom(topic: Topic): IMqttToken =
        if (messageListenerByTopic[topic] == null)
            error("Cannot unsubscribe from non existing Subscription of topic: [ $topic ]")
        else unsubscribe(topic.value)
            .await()
            .also { messageListenerByTopic.remove(topic) }

    fun connectToBroker() {
        setCallback(
            object : MqttCallback {
                override fun connectComplete(reconnect: Boolean, serverURI: String) =
                    mLogger.info("connected to broker: $serverURI").also {
                        mConfig.initialSubscriptions.forEach { subscription ->
                            subscribe(
                                subscription.topic.value,
                                subscription.qualityOfService.level
                            ).actionCallback = object : MqttActionListener {
                                override fun onSuccess(asyncActionToken: IMqttToken) =
                                    mLogger.info("successfully subscribed to topic: [ ${subscription.topic.value} ] with qos: [ ${subscription.qualityOfService.level} ]")

                                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) =
                                    mLogger.error("could not subscribeTo to topic: [ ${subscription.topic.value} ] due to: [ ${exception.message} ]")
                            }
                        }
                    }

                override fun authPacketArrived(reasonCode: Int, properties: MqttProperties) {
                    mLogger.debug("Auth reason code: [ $reasonCode ]  with reason: [ ${properties.reasonString} ]")
                }

                override fun disconnected(disconnectResponse: MqttDisconnectResponse) {
                    mLogger.warn("disconnected from broker due to: [ ${disconnectResponse.reasonString} ]")
                }

                override fun mqttErrorOccurred(exception: MqttException) {
                    mLogger.error("an error occurred: [ ${exception.message} ]")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    mLogger.debug("received ${message.toDebugString()} from topic [ $topic ]")
                    val validTopic = Topic(topic)
                    messageListenerByTopic[validTopic].let {
                        launch {
                            it?.invoke(TopicContext(validTopic, this@MqttClientPlugin), message)
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttToken) {
                    mLogger.debug("delivered message ${String(token.message.payload)} ")
                }
            }
        )
        connect(mConfig.connectionOptions).waitForCompletion()
    }

    suspend fun publishMessageTo(
        topic: Topic,
        msg: String,
        qos: QualityOfService,
        retained: Boolean
    ) {
        val message = MqttMessage()
        message.payload = msg.toByteArray()
        publish(topic.value, message.payload, qos.level, retained).await()
    }

}