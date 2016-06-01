# Developing a binary file protocol with Scodec and Akka Streams - Part 1

In this two part series, we will develop our very own file format to store stock prices as time series 
using Scodec and Akka Streams. The first part will describe the encoding / decoding process and how we 
improve our binary protocol step by step. In the second part, we will use Akka Streams to read and write 
our own files in chunks with the help of backpressure.

The complete project can be found [here on Github](https://github.com/MartinSeeler/dense-tick-file-format).

## The Goal

The goal of this post series is to show the usage and process of developing an efficient binary file protocol
to store stock prices and how to implement this with the help of Scodec. The file ending of our file format will be `dtff`, which stands for **d**ense **t**ick **f**ile **f**ormat. 

We will start by using CSV and improve it step by step, expecting around 1 million stock prices in a single file. The assumption we can make is that prices are 
ordered in our file and they must be stored without any loss of information. Let's get started by looking at what Scodec is.

## What is Scodec

[Scodec](http://scodec.org/) is a suite of purely functional Scala libraries for working with binary data. It is developed by [Michael Pilquist](https://github.com/mpilquist) and 
can be found on [Github](https://github.com/scodec/scodec). The great thing is that the mapping of binary structures to our types is statically verified with the help of [shapeless](https://github.com/milessabin/shapeless).

To get started with Scodec, we need to add some dependencies and bring them into scope.

```scala
libraryDependencies ++= List(
  "org.scodec" %% "scodec-core" % "1.9.0",
  "org.scodec" %% "scodec-bits" % "1.1.0"
)
```

```tut:silent
import scodec._
import bits._
import codecs._
```

Let's start by encoding an `Int` into a `BitVector`. To do this, we need a `Codec[Int]`. 
Gladfully, Scodec comes with a big number of predefined codecs, so we don't have to implement everything by our self. We can 
use `scodec.codecs.int32` do achieve what we want. Here is how to use it.

```tut:book
scodec.codecs.int32
int32 encode 42
```

Since Scodec is purely functional, our attempt to encode our number is represented as
`scodec.Attempt[scodec.bits.BitVector]`, which can be either `Successful` or a `Failure`. In our case, we had no problem to 
encode our integer. What we get is a `BitVector(32 bits, 0x0000002a)`. Here are some more examples to give you a feeling how to use Scodec.
 
```tut:book
// getting the binary representation
(int32 encode 42).getOrElse(BitVector.empty).toBin

// define a codec for an 8-bit unsigned int followed by an 8-bit unsigned int followed by a 16-bit unsigned int.
val codec = (uint8 ~ uint8 ~ uint16)

// using this codec to decode from a from bits
codec.decode(hex"0x2a2a0539".bits)

// using shapeless compile time magic for case class codecs
case class Point(x: Int, y: Int)
val pointCodec = (int32 :: int32).as[Point]
```

## Domain Model Definition

Now we know how we can use Scodec, so it's time to define our domain model. Stock prices are usually represented 
as tuple of an ask price and a bid price. In simple terms, you buy for the ask price and sell for the bid price. 
Stock prices are also constantly changing, so we have to specify a timestamp for which the price is valid. Each new price
update is called a tick, so our time series represents a series of ticks for an arbitrary stock.

```tut:silent
case class Tick(time: Long, bid: Double, ask: Double)
``` 

## The naive approach with CSV

The most trivial way to encode and store our ticks is using a comma or tab separated file.
It is easy to understand and can be used with a lot of applications, which makes it useful for all
these Excel evangelists and paper traders out there.

```tut:book
utf8 encode "1420148801108,1.20989,1.21049"
```

Looking at the output, we can see that one tick is encoded with 232 bits. For our 1 million tick file,
we can estimate a `232 * 1000000 / 8 / 1024 / 1024 = 27,65` MB file as a result. We will use this number as our 
base line and see, how much we can improve this number.

## Implementing our first codec

We will start by defining our first codec for our `Tick` case class. Our `Codec[Tick]` is defined 
as a single 64 bit Long number, followed by two 64-bit big endian IEEE 754 floating point numbers.

```tut:book
val tickCodec = (long(64) :: double :: double).as[Tick]
tickCodec encode Tick(1420148801108L, 1.20989, 1.21049)
```

Using our own codec, we only need 192 bits (3 * 64 bits) to encode a single tick, which is only 82% of our CSV approach. 
Below is the binary representation of our first tick. But we can do way better!

![Simple Codec](img/posts/dtff/simple-codec.png)

## Switching from Double to Int

The fact that a price is only represented up to a given precision makes it possible for us to eliminate our 
double values and use plain old integers instead. 

Prices can have a precision up to the fifth decimal place. 
As a consequence, we can use the factor `100.000` to multiply our `Double` and get an `Int` without loosing information. 
Let's define a new version for our ticks as `FactorizedTick` and some methods to easily switch between them.

```tut:book

val tick = Tick(1420148801108L, 1.20989, 1.21049)

case class FactorizedTick(time: Long, bid: Int, ask: Int)
val factorizedTickCodec = (long(64) :: int32 :: int32).as[FactorizedTick]

implicit final class TickOps(private val wrappedTick: Tick) extends AnyVal {
  def factorize: FactorizedTick =
    FactorizedTick(
      wrappedTick.time,
      (wrappedTick.bid * 100000).toInt,
      (wrappedTick.ask * 100000).toInt
    )
}

factorizedTickCodec encode tick.factorize
```

Using our factorized ticks, we only need 128 bits per tick. For our example file, this means
`128 * 1000000 / 8 / 1024 / 1024 = 15,25` MB in total. We can save 45% by using this 
method in comparison to the CSV version! Again, this is the binary representation of this codec. But that's not the end.

![Better Codec](img/posts/dtff/codec-2.png)
  
## Using delta encoding and Varints

Instead of storing each tick on it's own, we can make use of the fact that we want to safe an ordered series of ticks.
We only have to encode the first tick as usual. Every subsequent tick can be represented as the 
difference to the previous one. This method is called [Delta encoding](https://en.wikipedia.org/wiki/Delta_encoding).
  
```tut:book
case class FactorizedDeltaTick(timeDelta: Long, bidDelta: Int, askDelta: Int)
val factorizedDeltaTickCodec = (long(64) :: int32 :: int32).as[FactorizedDeltaTick]

implicit final class FactorizedTickOps(private val wrappedFactorizedTick: FactorizedTick) extends AnyVal {
  def deltaTo(prevFactorizedTick: FactorizedTick): FactorizedDeltaTick =
    FactorizedDeltaTick(
      wrappedFactorizedTick.time - prevFactorizedTick.time,
      wrappedFactorizedTick.bid - prevFactorizedTick.bid,
      wrappedFactorizedTick.ask - prevFactorizedTick.ask
    )
}

val otherTick = Tick(1420148801207L, 1.21004, 1.21063)

val delta = otherTick.factorize deltaTo tick.factorize
factorizedDeltaTickCodec encode delta
```

We can see that our delta defines the difference to the original tick, 
which is 99 milliseconds later, a `16 = 0,00016` higher bid price and
a `14 = ,00014` higher ask price. Sadly, we still need 128 bits to encode our delta, 
since every price difference is represented by 32 bits. 

This is where we can use [varints](http://usulusuldan.blogspot.de/2013/02/varints.html) or *variable length encoded* integers. 
Each byte in a varint, except the last byte, has the most significant bit set. 
In other terms, the highest bit of each byte encodes whether the next byte belongs to the current number. 
All other remaining 7 bits are used to hold the value itself. This makes it possible to decode an int with only 1 or up to 5 bytes, depending on the size of the number. 
Encoding primarily small numbers, using a variable length encoding can drastically save memory consumption. Since price changes are usually small,  this makes it perfect for our use case. 

Thanks to my friend [knutwalker](https://github.com/knutwalker), there is already a `vint` codec available in Scodec. 
Here is what it looks like for different numbers.

```tut:book
// saving 75%
int32 encode 42
vint encode 42

// saving 50%
int32 encode 1337
vint encode 1337

// using the same space
int32 encode 134217728
vint encode 134217728

// using 25% more space
int32 encode 2147483647
vint encode 2147483647
```

As we can see, small numbers are stored very efficient. The following is the new codec for our `FactorizedDeltaTick` case 
class using `vint` and `vlong`.

```tut:book
val factorizedDeltaTickCodecV = (vlong :: vint :: vint).as[FactorizedDeltaTick]
factorizedDeltaTickCodecV encode delta
```

This time, we reduced the memory consumption big times! Only 24 bits are needed to store our delta to the previous tick.
Of course this will not work every time. Since we only have 7 of 8 bits available in our `vint`, we can only represent numbers from 0 to 127 with one byte. 
When the price or time deltas are greater than 127, we need another byte. The following is the binary representation of our efficient delta tick.
 
![Delta Codec](img/posts/dtff/codec-3.png) 

So varints seems to be the holy grail to store stock prices. The truth is, this is only the case when the price changes upwards. Representing negative 
numbers, in our case a decreasing price delta, needs exactly 5 bytes. This is because the most significant bit
is used to indicate if the next byte is related to the current number, leaving us with only 7 bits remaining for our value. 
Therefore, 5 bytes are needed to represent negative numbers, even for small numbers.

```tut:book
vint encode -5

val negativeDelta = FactorizedDeltaTick(10, -5, -8)
factorizedDeltaTickCodec encode negativeDelta
```

What a wonderful world would it be when prices would only move upwards. In reality, prices move up and down all the time.
Even tough there is another way to store small positive and also negative numbers, which is called 
[ZigZag encoding](https://developers.google.com/protocol-buffers/docs/encoding#signed-integers), we will use a more trivial way to fix this issue.
We can use one bit to indicate, whether the next delta is positive (0) or negative (1) and then storing only the absolute value. Here is the case class and the codec for this 
non negative delta class.
 
```tut:book
case class NonNegativeFactorizedDeltaTick(timeDelta: Long, bidDeltaNeg: Boolean, bidDelta: Int, askDeltaNeg: Boolean, askDelta: Int)
val nonNegFactorizedDeltaTickCodecV = (vlong :: bool :: vint :: bool :: vint).as[NonNegativeFactorizedDeltaTick]

implicit class FactorizedDeltaTickOps(private val wrappedFactorizedDeltaTick: FactorizedDeltaTick) extends AnyVal {

  def nonNegative: NonNegativeFactorizedDeltaTick = NonNegativeFactorizedDeltaTick(
    wrappedFactorizedDeltaTick.timeDelta,
    wrappedFactorizedDeltaTick.bidDelta < 0,
    wrappedFactorizedDeltaTick.bidDelta.abs,
    wrappedFactorizedDeltaTick.askDelta < 0,
    wrappedFactorizedDeltaTick.askDelta.abs
  )
}

val nonNegativeDelta = negativeDelta.nonNegative
nonNegFactorizedDeltaTickCodecV encode nonNegativeDelta
```
 
Now we've got it. Even negative deltas can now be stored very efficient using varints. We did increase 
the minimum size from 24 to 26 bits with our two bits as negative indicators, but we decreased the overall 
storage needed for up and down movements. Expecting evenly distributed up and down moves, we decreased 
our average storage space from 77 bits (26 bits for positives and 128 bits for negatives) down to just 26 bits! 
This makes it possible to store 1 million ticks inside an approximately `26 * 1000000 / 8 / 1024 / 1024 = 3,1` MB file, 
which is a decrease of 88,79% for the total file size in comparison to the CSV approach! 

Here is the final binary representation for our codec.

![Simple Codec](img/posts/dtff/codec-4.png)

## Conclusion

By developing our own binary protocol to store stock prices as ordered time series, we were able to improve
the storage efficiency by nearly 90%. We started with 232 bits for one tick in CSV format and improved
 the storage needed step by step. The first step was storing integers instead of double values. After that, we switched to *delta
 encoding* in combination with *variable length encoded integers* to make use of the small price changes for each price update.
Whenever we have structured data and we care about efficient memory usage, it makes sense to consider building your own binary file protocol by using easy libraries like Scodec
 to represent your own codec.
 
In the next part of this series, we will use Akka Streams to build a Source and a Sink to read and write huge files 
of our own file protocol efficiently with the help of backpressure. The full source code of the final result can 
be found [here on Github](https://github.com/MartinSeeler/dense-tick-file-format).   
