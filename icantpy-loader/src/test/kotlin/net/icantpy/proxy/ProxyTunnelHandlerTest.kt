package net.icantpy.proxy

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioSocketChannel
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProxyTunnelHandlerTest {
    private val group = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    private val target = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(10, 1, 2, 3)), 25565)

    @AfterTest
    fun shutdown() {
        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync()
    }

    /** Fake proxy: runs [script] for one connection, then echoes whatever the client sends through the tunnel. */
    private fun fakeProxy(script: (DataInputStream, DataOutputStream) -> Unit): ServerSocket {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                script(input, output)
                val echo = ByteArray(5)
                input.readFully(echo)
                output.write(echo)
                output.flush()
            }
        }
        return server
    }

    private class Client {
        val activeBeforeConnectDone = CompletableFuture<Boolean>()
        val received = CompletableFuture<String>()
    }

    private fun connect(settings: ProxySettings, client: Client): Channel {
        val future = Bootstrap().group(group).channel(NioSocketChannel::class.java).handler(object : ChannelInitializer<Channel>() {
            override fun initChannel(channel: Channel) {
                channel.pipeline().addLast(object : ChannelInboundHandlerAdapter() {
                    override fun channelActive(ctx: ChannelHandlerContext) {
                        client.activeBeforeConnectDone.complete(true)
                        ctx.writeAndFlush(Unpooled.wrappedBuffer("hello".toByteArray()))
                    }

                    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                        val buf = msg as ByteBuf
                        client.received.complete(buf.toString(Charsets.US_ASCII))
                        buf.release()
                    }
                })
                channel.pipeline().addFirst(ProxyTunnelHandler.NAME, ProxyTunnelHandler(settings))
            }
        }).connect(target)
        future.await(5, TimeUnit.SECONDS)
        if (!future.isSuccess) throw future.cause()
        return future.channel()
    }

    @Test
    fun socks5WithAuthTunnelsExactBytes() {
        val proxy = fakeProxy { input, output ->
            val greeting = ByteArray(4).also(input::readFully)
            assertContentEquals(byteArrayOf(5, 2, 0, 2), greeting)
            output.write(byteArrayOf(5, 2)); output.flush()
            assertEquals(1, input.readUnsignedByte())
            val user = ByteArray(input.readUnsignedByte()).also(input::readFully)
            val pass = ByteArray(input.readUnsignedByte()).also(input::readFully)
            assertEquals("alt" to "secret", String(user) to String(pass))
            output.write(byteArrayOf(1, 0)); output.flush()
            val request = ByteArray(10).also(input::readFully)
            assertContentEquals(byteArrayOf(5, 1, 0, 1, 10, 1, 2, 3, 0x63, 0xDD.toByte()), request)
            output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush()
        }
        val client = Client()
        val channel = connect(ProxySettings(ProxySettings.Type.SOCKS5, "127.0.0.1", proxy.localPort, "alt", "secret"), client)
        assertEquals("hello", client.received.get(5, TimeUnit.SECONDS))
        assertNull(channel.pipeline().get(ProxyTunnelHandler.NAME), "handler removes itself after the handshake")
        channel.close().sync()
        proxy.close()
    }

    @Test
    fun httpConnectSendsBasicAuth() {
        val proxy = fakeProxy { input, output ->
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) head.append(input.readUnsignedByte().toChar())
            assertTrue(head.startsWith("CONNECT 10.1.2.3:25565 HTTP/1.1\r\n"), head.toString())
            assertTrue(head.contains("Proxy-Authorization: Basic YWx0OnNlY3JldA==\r\n"), head.toString())
            output.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray()); output.flush()
        }
        val client = Client()
        val channel = connect(ProxySettings(ProxySettings.Type.HTTP, "127.0.0.1", proxy.localPort, "alt", "secret"), client)
        assertEquals("hello", client.received.get(5, TimeUnit.SECONDS))
        channel.close().sync()
        proxy.close()
    }

    @Test
    fun rejectedAuthFailsWithoutDirectFallback() {
        val proxy = fakeProxy { input, output ->
            input.readFully(ByteArray(4))
            output.write(byteArrayOf(5, 0xFF.toByte())); output.flush()
            Thread.sleep(200)
            throw IllegalStateException("done") // close the socket
        }
        val client = Client()
        val failure = runCatching { connect(ProxySettings(ProxySettings.Type.SOCKS5, "127.0.0.1", proxy.localPort, "alt", "bad"), client) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()!!.message!!.startsWith("Proxy:"), failure.exceptionOrNull()!!.message)
        assertFalse(client.activeBeforeConnectDone.isDone, "Minecraft's handler must never see an active channel")
        proxy.close()
    }

    @Test
    fun parsesSellerFormats() {
        val colon = ProxySettings.parse(ProxySettings.Type.SOCKS5, "1.2.3.4:1080:user:pa:ss")
        assertNull(colon, "five parts is ambiguous and rejected")
        val four = ProxySettings.parse(ProxySettings.Type.SOCKS5, "1.2.3.4:1080:user:pass")!!
        assertEquals(ProxySettings(ProxySettings.Type.SOCKS5, "1.2.3.4", 1080, "user", "pass"), four)
        val url = ProxySettings.parse(ProxySettings.Type.SOCKS5, "http://user:pa:ss@proxy.example:8080")!!
        assertEquals(ProxySettings(ProxySettings.Type.HTTP, "proxy.example", 8080, "user", "pa:ss"), url)
        assertNull(ProxySettings.parse(ProxySettings.Type.SOCKS5, "proxy.example"))
        assertNull(ProxySettings.parse(ProxySettings.Type.SOCKS5, "proxy.example:99999"))
    }

    @Test
    fun perAccountEntryOverridesDefault() {
        val default = ProxySettings(ProxySettings.Type.SOCKS5, "a", 1, "", "")
        val alt = ProxySettings(ProxySettings.Type.HTTP, "b", 2, "", "")
        val state = ProxyConfig.State(true, default, mapOf("myalt" to alt))
        assertEquals(alt, state.resolve("MyAlt"))
        assertEquals(default, state.resolve("main"))
        assertNull(ProxyConfig.State(false, default, mapOf()).resolve("main"))
    }
}
