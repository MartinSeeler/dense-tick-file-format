# Dense Tick File Format

> A proof of concept for an efficient stock price file format based on Akka Streams and Scodec.

## About the project

This project demonstrates the usage of [Scodec](http://scodec.org/) and [Akka Streams](http://doc.akka.io/docs/akka/2.4.6/scala/stream/index.html) 
to develop an efficient binary file protocol to store stock prices. We will use variable length encoding and delta encoding to improve the storage efficiency step by step.

You can read the whole process in this [two part series on my blog](http://www.martinseeler.com/developing-efficient-bianry-file-protocol-with-scodec-and-akka-streams.html).
