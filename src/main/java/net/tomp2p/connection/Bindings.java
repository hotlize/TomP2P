/*
 * Copyright 2009 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.connection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class Bindings
{
	public enum Protocol
	{
		IPv4, IPv6
	};
	// we need to have IPv4 as first priority, as IPv6 does not seem to work
	// properly
	final private List<InetAddress> listenAddresses4 = new ArrayList<InetAddress>(1);
	final private List<InetAddress> listenAddresses6 = new ArrayList<InetAddress>(1);
	final private List<InetAddress> broadcastAddresses = new ArrayList<InetAddress>(1);
	final private List<String> listenInterfaceHints = new ArrayList<String>(1);
	final private List<Protocol> listenProtocolHints = new ArrayList<Protocol>(0);
	final private boolean listenBroadcast;
	private InetAddress outsideAddress = null;
	private int outsideTCPPort = 0;
	private int outsideUDPPort = 0;

	public Bindings()
	{
		this(true);
	}

	public Bindings(InetAddress bind)
	{
		addAddress(bind);
		this.listenBroadcast = true;
	}

	public Bindings(boolean listenBroadcast)
	{
		this.listenBroadcast = listenBroadcast;
	}

	public Bindings(Protocol protocol)
	{
		addProtocol(protocol);
		this.listenBroadcast = true;
	}

	public Bindings(String iface)
	{
		addInterface(iface);
		this.listenBroadcast = true;
	}

	public Bindings(Protocol protocol, String iface)
	{
		addInterface(iface);
		addProtocol(protocol);
		this.listenBroadcast = true;
	}

	public Bindings(Protocol protocol, String iface, InetAddress outsideAddress,
			int outsideTCPPort, int outsideUDPPort, boolean listenBroadcast)
	{
		addInterface(iface);
		addProtocol(protocol);
		this.outsideAddress = outsideAddress;
		this.outsideTCPPort = outsideTCPPort;
		this.outsideUDPPort = outsideUDPPort;
		this.listenBroadcast = listenBroadcast;
	}

	public void addAddress(InetAddress address)
	{
		if (address == null)
			throw new IllegalArgumentException("Cannot add null");
		if (address instanceof Inet4Address)
			listenAddresses4.add(address);
		else
			listenAddresses6.add(address);
	}

	public void addBroadcastAddress(InetAddress broadcastAddress)
	{
		if (broadcastAddress == null)
			throw new IllegalArgumentException("Cannot add null");
		broadcastAddresses.add(broadcastAddress);
	}

	public List<InetAddress> getAddresses()
	{
		// first return ipv4, then ipv6
		List<InetAddress> listenAddresses = new ArrayList<InetAddress>();
		listenAddresses.addAll(listenAddresses4);
		listenAddresses.addAll(listenAddresses6);
		return listenAddresses;
	}

	public List<InetAddress> getBroadcastAddresses()
	{
		return broadcastAddresses;
	}

	public void addInterface(String interfaceHint)
	{
		if (interfaceHint == null)
			throw new IllegalArgumentException("Cannot add null");
		listenInterfaceHints.add(interfaceHint);
	}

	public List<String> getInterfaces()
	{
		return listenInterfaceHints;
	}

	public void addProtocol(Protocol protocol)
	{
		if (protocol == null)
			throw new IllegalArgumentException("Cannot add null");
		listenProtocolHints.add(protocol);
	}

	public List<Protocol> getProtocols()
	{
		return listenProtocolHints;
	}

	public void setAllInterfaces()
	{
		listenInterfaceHints.clear();
	}

	public boolean useAllInterfaces()
	{
		return listenInterfaceHints.size() == 0;
	}

	public void setAllProtocols()
	{
		listenProtocolHints.clear();
	}

	public boolean useAllProtocols()
	{
		return listenProtocolHints.size() == 0;
	}

	public boolean useIPv4()
	{
		return useAllProtocols() || listenInterfaceHints.contains(Protocol.IPv4);
	}

	public boolean useIPv6()
	{
		return useAllProtocols() || listenInterfaceHints.contains(Protocol.IPv6);
	}

	private StringBuilder discoverNetwork(NetworkInterface networkInterface)
	{
		StringBuilder sb = new StringBuilder();
		for (InterfaceAddress iface : networkInterface.getInterfaceAddresses())
		{
			InetAddress inet = iface.getAddress();
			if (getAddresses().contains(inet))
				continue;
			if (useAllProtocols())
			{
				sb.append(",").append(inet);
				addAddress(inet);
			}
			else
			{
				if (inet instanceof Inet4Address && getProtocols().contains(Protocol.IPv4))
				{
					sb.append(",").append(inet);
					addAddress(inet);
				}
				if (inet instanceof Inet6Address && getProtocols().contains(Protocol.IPv6))
				{
					sb.append(",").append(inet);
					addAddress(inet);
				}
			}
			if (iface.getBroadcast() != null)
				addBroadcastAddress(iface.getBroadcast());
		}
		return sb.replace(0, 1, "(").append(")");
	}

	public String discoverLocalInterfaces() throws SocketException, UnknownHostException
	{
		StringBuilder sb = new StringBuilder("Status: ");
		Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
		while (e.hasMoreElements())
		{
			NetworkInterface networkInterface = e.nextElement();
			if (useAllInterfaces())
			{
				sb.append(" +").append(networkInterface.getName());
				sb.append(discoverNetwork(networkInterface)).append(",");
			}
			else
			{
				if (getInterfaces().contains(networkInterface.getName()))
				{
					sb.append(" +").append(networkInterface.getName());
					sb.append(discoverNetwork(networkInterface)).append(",");
				}
				else
					sb.append(" -").append(networkInterface.getName()).append(",");
			}
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}

	public boolean isListenBroadcast()
	{
		return listenBroadcast;
	}

	public void setOutsideAddress(InetAddress outsideAddress, int outsideTCPPort, int outsideUDPPort)
	{
		if (outsideAddress == null)
			throw new IllegalArgumentException("address cannot be null");
		if (outsideTCPPort <= 0 || outsideUDPPort <= 0)
			throw new IllegalArgumentException("port needs to be > 0");
		this.outsideAddress = outsideAddress;
		this.outsideTCPPort = outsideTCPPort;
		this.outsideUDPPort = outsideUDPPort;
	}

	public InetAddress getOutsideAddress()
	{
		return outsideAddress;
	}

	public int getOutsideTCPPort()
	{
		return outsideTCPPort;
	}

	public int getOutsideUDPPort()
	{
		return outsideUDPPort;
	}
}