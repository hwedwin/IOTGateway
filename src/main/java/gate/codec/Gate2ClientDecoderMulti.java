package gate.codec;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import gate.base.domain.ChannelData;
import gate.base.domain.SocketData;
import gate.util.CommonUtil;
import gate.util.StringUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.internal.RecyclableArrayList;
/**
 * 解码器
 * @Description: 
 * @author  yangcheng
 * @date:   2019年6月11日
 */
public class Gate2ClientDecoderMulti  extends ByteToMessageDecoder{
	private int pId;
	private boolean isBigEndian ;
	private int beginHexVal;
	private int lengthFieldOffset;
	private int lengthFieldLength;//值为Data得长度
	private boolean isDataLenthIncludeLenthFieldLenth ;//长度域长度值是否包含长度域本身长度
	private int exceptDataLenth;
	private int initialBytesToStrip;//默认为0
	
	/**
	 * 全参构造
	 * @param pId
	 * @param isBigEndian
	 * @param beginHexVal
	 * @param lengthFieldOffset
	 * @param lengthFieldLength
	 * @param isDataLenthIncludeLenthFieldLenth
	 * @param exceptDataLenth
	 * @param initialBytesToStrip
	 */
	public Gate2ClientDecoderMulti( int pId, boolean isBigEndian, int beginHexVal, int lengthFieldOffset, int lengthFieldLength,
			boolean isDataLenthIncludeLenthFieldLenth, int exceptDataLenth, int initialBytesToStrip) {
		super();
		this.pId = pId;
		this.isBigEndian = isBigEndian;
		this.beginHexVal = beginHexVal;
		this.lengthFieldOffset = lengthFieldOffset;
		this.lengthFieldLength = lengthFieldLength;
		this.isDataLenthIncludeLenthFieldLenth = isDataLenthIncludeLenthFieldLenth;
		this.exceptDataLenth = exceptDataLenth;
		this.initialBytesToStrip = initialBytesToStrip;
	}
	
	/**
	 * 默认起始偏移量为0
	 * @param pId
	 * @param isBigEndian
	 * @param beginHexVal
	 * @param lengthFieldOffset
	 * @param lengthFieldLength
	 * @param isDataLenthIncludeLenthFieldLenth
	 * @param exceptDataLenth
	 */
	public Gate2ClientDecoderMulti(int pId, boolean isBigEndian, int beginHexVal, int lengthFieldOffset, int lengthFieldLength,
			boolean isDataLenthIncludeLenthFieldLenth, int exceptDataLenth) {
		this(pId, isBigEndian, beginHexVal, lengthFieldOffset, lengthFieldLength,
				isDataLenthIncludeLenthFieldLenth, exceptDataLenth, 0);
	}




	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		int baseLen = lengthFieldOffset + lengthFieldLength + exceptDataLenth + initialBytesToStrip;
		if(in.readableBytes()>= baseLen){
			int beginReader;
			RecyclableArrayList arrayList = RecyclableArrayList.newInstance();
			while (true) {
				if(in.readableBytes()>= baseLen){
					beginReader = in.readerIndex();
					if(beginHexVal > 0){
						int startHexVal = in.readByte() & 0xFF;
						if(startHexVal != beginHexVal){
							dataTransfer(arrayList,out);
							continue;
						}
						in.readerIndex(beginReader);
					}
					
					ByteBuf byteBuf = CommonUtil.getDirectByteBuf();
					
					if(initialBytesToStrip == 0){
						byteBuf.writeBytes(in.readBytes(lengthFieldOffset));
						
						ByteBuf lenAre = in.readBytes(lengthFieldLength);
						byteBuf.writeBytes(lenAre);
						lenAre.readerIndex(0);
						int lenVal = 0;
						switch (lengthFieldLength) {
						case 1:
								lenVal = lenAre.readByte() & 0xFF;
							break;
						case 2:
							if(isBigEndian){
								lenVal = lenAre.readShort() & 0xFFFF;
							}else{
								lenVal = lenAre.readShortLE() & 0xFFFF ;
							}
							break;
						case 4:
							if(isBigEndian){
								lenVal = lenAre.readInt();
							}else{
								lenVal = lenAre.readIntLE();
							}
							break;
						default:
							CommonUtil.releaseByteBuf(byteBuf);
							break;
						}
						if(isDataLenthIncludeLenthFieldLenth){
							lenVal = lenVal - lengthFieldLength;
						}
						
						if(in.readableBytes() >= (lenVal+exceptDataLenth)  && lenVal>0){
							byteBuf.writeBytes(in.readBytes(lenVal+exceptDataLenth));
//							in.markReaderIndex();
							Channel channel = ctx.channel();
							InetSocketAddress insocket = (InetSocketAddress)channel.remoteAddress();
							String ipAddress = StringUtils.formatIpAddress(insocket.getHostName(), String.valueOf(insocket.getPort()));
							String clientIpAddress = ipAddress;
							SocketData data = new SocketData(byteBuf);
							data.setpId(pId);
							ChannelData channelData =  new ChannelData(clientIpAddress, data);
							arrayList.add(channelData);
							continue;
						}else{
							if(beginHexVal < 0 ){
								in.readerIndex(beginReader+1);
							}else{
								in.readerIndex(beginReader);
							}
							dataTransfer(arrayList,out);
							break;
						}
						
					}else{
						byteBuf.writeBytes(in.readBytes(initialBytesToStrip));
						byteBuf.writeBytes(in.readBytes(lengthFieldOffset));
						ByteBuf lenAre = in.readBytes(lengthFieldLength);
						byteBuf.writeBytes(lenAre);
						lenAre.readerIndex(0);
						int lenVal = 0;
						switch (lengthFieldLength) {
						case 1:
								lenVal = lenAre.readByte() & 0xFF;
							break;
						case 2:
							if(isBigEndian){
								lenVal = lenAre.readShort() & 0xFFFF;
							}else{
								lenVal = lenAre.readShortLE() & 0xFFFF;
							}
							break;
						case 4:
							if(isBigEndian){
								lenVal = lenAre.readInt();
							}else{
								lenVal = lenAre.readIntLE();
							}
							break;
						default:
							CommonUtil.releaseByteBuf(byteBuf);
							break;
						}
						if(isDataLenthIncludeLenthFieldLenth){
							lenVal = lenVal - lengthFieldLength;
						}
						
						if(in.readableBytes() >= (lenVal+exceptDataLenth) && lenVal>0){
							byteBuf.writeBytes(in.readBytes(lenVal+exceptDataLenth));
//							in.markReaderIndex();
							Channel channel = ctx.channel();
							InetSocketAddress insocket = (InetSocketAddress)channel.remoteAddress();
							String ipAddress = StringUtils.formatIpAddress(insocket.getHostName(), String.valueOf(insocket.getPort()));
							String clientIpAddress = ipAddress;
							SocketData data = new SocketData(byteBuf);
							data.setpId(pId);
							ChannelData channelData =  new ChannelData(clientIpAddress, data);
							arrayList.add(channelData);
							continue;
						}else{
							if(beginHexVal < 0 ){
								in.readerIndex(beginReader+1);
							}else{
								in.readerIndex(beginReader);
							}
							dataTransfer(arrayList,out);
							break;
						}
					}
				}else{
					dataTransfer(arrayList,out);
					break;
				}
			}
		}
	}
	private void dataTransfer(RecyclableArrayList arrayList,List<Object> out){
		if(!arrayList.isEmpty()){
			int size = arrayList.size();
			ArrayList<Object> arrayList2 = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				arrayList2.add(arrayList.get(i));
			}
			out.add(arrayList2);
			arrayList.recycle();
		}
	}
}
