import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import java.net.InetSocketAddress
import io.ktor.utils.io.*
import kotlinx.coroutines.*

fun isWin(): Char {

    val list = listOf(
        Pair<Int, Int>(0, 1),
        Pair<Int, Int>(3, 1),
        Pair<Int, Int>(6, 1),
        Pair<Int, Int>(0, 3),
        Pair<Int, Int>(1, 3),
        Pair<Int, Int>(2, 3),
        Pair<Int, Int>(0, 4),
        Pair<Int, Int>(2, 2),
    )

    val cols = mutableListOf<String>()

    for (x in list) {
        var s = ""
        for (i in x.first..x.first + x.second * 3 step x.second) {
            s += field.fieldSymbols[i]
        }
        cols.add(s)
    }
    if ("XXX" in cols)
        return 'X'

    if ("OOO" in cols)
        return 'O'

    if ('_' in field.fieldSymbols)
        return 'N'

    return 'D'
}

fun ticktacktoeServer(hostname: String, port: Int) = runBlocking {
    val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind(InetSocketAddress(hostname, port))
    println("Started summation server at ${server.localAddress}")

    val side = 'X'

    val socket = server.accept()
    println("подключено")
    val input = socket.openReadChannel()
    val output = socket.openWriteChannel(autoFlush = true)

    field.currentTurnChoise = -1
    field.isMyTurn = ('X' == side)
    while (true) {
        if (field.isMyTurn) {

            while ((field.currentTurnChoise == -1) || (field.fieldSymbols[field.currentTurnChoise] != '_')) {
                delay(50)
            }
            field.isMyTurn = false
            field.fieldSymbols[field.currentTurnChoise] = side

            output.writeInt(field.currentTurnChoise)
        } else {
            val opp = input.readInt()

            if (opp == -1) {
                println("потеря соединения")
                assert(false)
            }

            field.fieldSymbols[opp] = when (side) {
                'X' -> 'O'
                else -> 'X'
            }
            field.isMyTurn = true
        }
    }
}


fun startAsServer(hostname: String, port: Int) = runBlocking {
    State.isServer = true
    val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind(InetSocketAddress(hostname, port))
    val socket = server.accept()
    State.input = socket.openReadChannel()
    try {

        while (true) {
            val line = State.input?.readUTF8Line() ?: break
            val coords = line.split(" ").map { it.toFloat() }
            if (coords.size == 2) {
                State.points.add(Point(coords[0], coords[1], true))
            }
        }
    } finally {
        socket.close()
        server.close()
    }
}

fun startAsClient(hostname: String, port: Int) = runBlocking {
    State.isServer = false
    val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().connect(InetSocketAddress(hostname, port))
    State.output = socket.openWriteChannel(autoFlush = true)
}

fun sendMouseCoordinates(mouseX: Float, mouseY: Float) {
    GlobalScope.launch(Dispatchers.IO) {
        State.output?.writeStringUtf8("$mouseX $mouseY\n")
    }
}

fun startNetworking(args: Array<String>, hostname: String, port: Int) {
    if (args.size == 1) {
        when (args[0]) {
            "client" -> startAsClient(hostname, port)
//            "server" -> startAsServer(hostname, port)
            "server" -> ticktacktoeServer(hostname, port)
        }
    }
}