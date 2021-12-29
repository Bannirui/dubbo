/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Decodeable;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;

public class DecodeHandler extends AbstractChannelHandlerDelegate {

    private static final Logger log = LoggerFactory.getLogger(DecodeHandler.class);

    public DecodeHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        if (message instanceof Decodeable) { // Decodeable类型消息 对整个消息解码
            decode(message);
        }

        if (message instanceof Request) { // Request类型的请求消息 对请求数据解码
            decode(((Request) message).getData());
        }

        if (message instanceof Response) { // Response类型的返回数据 对返回结果解码
            decode(((Response) message).getResult());
        }

        handler.received(channel, message); // 将消息委托给handler继续处理
    }

    private void decode(Object message) {
        if (message instanceof Decodeable) { // 当消息是Decodeable类型的时候继续进行解析
            try {
                ((Decodeable) message).decode();
            } catch (Throwable e) {
            } // ~ end of catch
        } // ~ end of if
    } // ~ end of method decode

}
