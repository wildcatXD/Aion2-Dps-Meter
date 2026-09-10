package com.tbread.packet

import com.tbread.config.PcapCapturerConfig
import kotlinx.coroutines.channels.Channel
import org.pcap4j.core.*
import org.pcap4j.packet.IpV4Packet
import org.pcap4j.packet.TcpPacket
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.InetAddress

data class CapturedPacket(val ip: String, val seq: Long, val data: ByteArray, val arrivedAt: Long)

class PcapCapturer(private val config: PcapCapturerConfig, private val channel: Channel<CapturedPacket>) {

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)

        private fun getAllDevices(): List<PcapNetworkInterface> {
            return try {
                Pcaps.findAllDevs() ?: emptyList()
            } catch (e: PcapNativeException) {
                logger.error("Pcap 핸들러 초기화 실패", e)
                emptyList()
            }
        }
    }

    private fun getMainDevice(ip: String): PcapNetworkInterface? {
        val devices = getAllDevices()
        for (device in devices) {
            for (addr in device.addresses) {
                if (addr.address != null) {
                    if (addr.address.hostAddress.equals(ip)) {
                        return device
                    }
                }
            }
        }
        logger.warn("네트워크 디바이스 검색 실패")
        return null
    }


    fun start() {
        while (true) {
            try {
                startOnce()
                return
            } catch (e: InterruptedException) {
                logger.error("패킷 캡처가 중단되었습니다", e)
                return
            } catch (e: Exception) {
                logger.error("패킷 캡처 실패, 5초 후 재시도합니다. 오버레이는 그대로 둡니다", e)
                try {
                    Thread.sleep(5000)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    private fun startOnce() {
        val socket = DatagramSocket()
        socket.connect(InetAddress.getByName(config.serverIp.split("/")[0]), config.serverPort.toInt())
        val ip = socket.localAddress.hostAddress
        if (ip == null) {
            throw IllegalStateException("ip 검색에 실패했습니다.")
        }
        val nif = getMainDevice(ip)
            ?: throw IllegalStateException("네트워크 디바이스 탐색에 실패했습니다. 관리자 권한/Npcap을 확인하세요.")
        val handle = nif.openLive(config.snapshotSize, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, config.timeout)
        val filter = "src net ${config.serverIp} and port ${config.serverPort}"
        handle.setFilter(filter, BpfProgram.BpfCompileMode.OPTIMIZE)
        logger.info("패킷필터 설정 \"$filter\"")
        val listener = PacketListener { packet ->
            if (packet.contains(TcpPacket::class.java)) {
                val tcpPacket = packet.get(TcpPacket::class.java)
                val payload = tcpPacket.payload
                if (payload != null) {
                    val data = payload.rawData
                    if (data.isNotEmpty()) {
                        val srcIp = packet.get(IpV4Packet::class.java).header.srcAddr.hostAddress
                        val seq = tcpPacket.header.sequenceNumber.toLong() and 0xffffffffL
                        channel.trySend(CapturedPacket(srcIp, seq, data, System.currentTimeMillis()))
                    }
                }
            }
        }
        handle.use { h ->
            h.loop(-1, listener)
        }
    }


}