package dev._2lstudios.hamsterapi.utils;

import dev._2lstudios.hamsterapi.wrappers.PacketWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class BufferIO {
	private final Class<?> packetDataSerializerClass, networkManagerClass, enumProtocolClass,
			enumProtocolDirectionClass;
	private final Inflater inflater;
	private final int bukkitVersion;
	private final int compressionThreshold;

	public BufferIO(final Reflection reflection, final String bukkitVersion, final int compressionThreshold) {
		this.packetDataSerializerClass = reflection.getPacketDataSerializer();
		this.networkManagerClass = reflection.getNetworkManager();
		this.enumProtocolClass = reflection.getEnumProtocol();
		this.enumProtocolDirectionClass = reflection.getEnumProtocolDirection();
		this.inflater = new Inflater();
		this.bukkitVersion = bukkitVersion == null ? 1206 : Integer.parseInt(bukkitVersion);
		this.compressionThreshold = compressionThreshold;
	}

	public ByteBuf split(final ByteBuf bytebuf) throws DecoderException, IOException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, InstantiationException, NoSuchFieldException {
		bytebuf.markReaderIndex();
		byte[] abyte = new byte[3];

		for (int i = 0; i < abyte.length; ++i) {
			if (!bytebuf.isReadable()) {
				bytebuf.resetReaderIndex();
				throw new DecoderException("Unreadable byte");
			}

			abyte[i] = bytebuf.readByte();
			if (abyte[i] >= 0) {
				final Object packetDataSerializer = packetDataSerializerClass.getConstructor(ByteBuf.class)
						.newInstance(Unpooled.wrappedBuffer(abyte));

				try {
					final Method packetDataSerializerBytes = packetDataSerializerClass.getMethod("e");
					final int bytes = (int) packetDataSerializerBytes.invoke(packetDataSerializer);

					if (bytebuf.readableBytes() >= bytes) {
						return bytebuf.readBytes(bytes);
					}

					bytebuf.resetReaderIndex();
				} finally {
					packetDataSerializerClass.getMethod("release").invoke(packetDataSerializer);
				}

				throw new DecoderException("Too much unreadeable bytes");
			}
		}

		return null;
	}

	public ByteBuf decompress(final ByteBuf byteBuf) throws DecoderException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, InstantiationException, DataFormatException {
		if (byteBuf.readableBytes() != 0 && compressionThreshold > -1) {
			final Object packetData = packetDataSerializerClass.getConstructor(ByteBuf.class).newInstance(byteBuf);
			final Class<?> packetDataClass = packetData.getClass();
			final int bytes = (int) packetDataClass.getMethod("e").invoke(packetData); // packetData.e()

			if (bytes == 0) {
				return (ByteBuf) packetDataClass.getMethod("readBytes", int.class).invoke(packetData,
						(int) packetDataClass.getMethod("readableBytes").invoke(packetData));
			}

			if (bytes < compressionThreshold) {
				throw new DecoderException("[BufferIO] Badly compressed packet - size of " + bytes
						+ " is below server threshold of " + compressionThreshold);
			} else if (bytes > 2097152) {
				throw new DecoderException("[BufferIO] Badly compressed packet - size of " + bytes
						+ " is larger than protocol maximum of " + 2097152);
			}

			final byte[] abyte = new byte[(int) packetDataClass.getMethod("readableBytes").invoke(packetData)];

			packetDataClass.getMethod("readBytes", byte[].class).invoke(packetData, abyte);
			inflater.setInput(abyte);

			final byte[] bbyte = new byte[bytes];

			inflater.inflate(bbyte);
			inflater.reset();

			return Unpooled.wrappedBuffer(bbyte);
		} else {
			return byteBuf;
		}
	}

	public PacketWrapper decode(final ChannelHandlerContext chx, final ByteBuf byteBuf, final int maxCapacity)
			throws DecoderException, IOException, IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, InstantiationException, NoSuchFieldException {
		if (byteBuf.readableBytes() != 0) {
			final int capacity = byteBuf.capacity();

			if (capacity > maxCapacity) {
				if (byteBuf.refCnt() > 0) {
					byteBuf.clear();
				}

				throw new DecoderException("[BufferIO] Max decoder capacity exceeded. capacity: " + capacity);
			}

			final Channel channel = chx.channel();
			final Object packetDataSerializer = packetDataSerializerClass.getConstructor(ByteBuf.class)
					.newInstance(byteBuf);
			final Class<?> packetDataClass = packetDataSerializer.getClass();
			final int id;

			if (bukkitVersion > 1122) {
				id = (int) packetDataClass.getMethod("g").invoke(packetDataSerializer);
			} else {
				id = (int) packetDataClass.getMethod("e").invoke(packetDataSerializer);
			}

			final AttributeKey<?> attributeKey = (AttributeKey<?>) networkManagerClass.getDeclaredField("c").get(null);
			final Object attribute = channel.attr(attributeKey).get();
			final Object packet = enumProtocolClass.getMethod("a", enumProtocolDirectionClass, int.class)
					.invoke(enumProtocolClass.cast(attribute),
							enumProtocolDirectionClass.getField("SERVERBOUND").get(null), id);

			if (packet == null) {
				throw new IOException("[BufferIO] Bad packet received. id: " + id);
			}

			final Class<?> packetClass = packet.getClass();

			packetClass.getMethod("a", packetDataSerializerClass).invoke(packet, packetDataSerializer);

			return new PacketWrapper(packet);
		} else {
			return null;
		}
	}
}