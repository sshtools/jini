/**
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sshtools.jini.prefs;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sshtools.jini.serialization.INISerialization.INIField;
import com.sshtools.jini.serialization.INISerialization.INIMethod;
import com.sshtools.jini.serialization.INISerialization.INISerialized;
import com.sshtools.jini.serialization.INISerialization.Rule;

public abstract class AbstractSerializerTest {

	protected final static String INI_TEXT = //
			"name = Joe B\n" + //
					"age = 32\n" + //
					"sendSpam = true\n" + //
					"signature = \"AQIDBAU=\"\n" + //
					"payload = \"oLDA0OA=\"\n" + //
					"telephones = 123 456789, 987 654321\n" + //
					"favouriteColour = GREEN\n" + //
					"\n" + //
					"[props]\n" + //
					"  Prop1 = Str1\n" + //
					"  Prop2 = Str2\n" + //
					"\n" + //
					"[items]\n" + //
					"  key = An Orange\n" + //
					"  number = 123\n" + //
					"\n" + //
					"[items]\n" + //
					"  key = A Bag Of Dreamies\n" + //
					"  number = 456\n" + //
					"\n" + //
					"[items]\n" + //
					"  key = Fourteen Pairs Of Pink Slippers\n" + //
					"  number = 789\n" + //
					"\n" + //
					"[address]\n" + //
					"  streetAddress = 11 Some Road\n" + //
					"  city = London\n" + //
					"  county = Essex\n" + //
					"\n" + //
					"  [address.country]\n" + //
					"    name = United Kingdom\n" + //
					"    code = UK\n"; //


	
	final static Country UK = new Country("United Kingdom", "UK");
	
	public enum FavouriteColour {
		RED, GREEN, BLUE
	}
	
	@INISerialized(rule = Rule.EXCLUDE)
	public final static class Country {		
		@INIField(rule = Rule.INCLUDE)
		private String name;
		private String code;

		public Country() {
		}
		
		public Country(String name, String code) {
			super();
			this.name = name;
			this.code = code;
		}
		
		@INIMethod(rule = Rule.INCLUDE)
		public String getCode() {
			return code;
		}

		/* TODO make this better, so its not needed if a getter is there or vice versa */
		@INIMethod(rule = Rule.INCLUDE)
		public void setCode(String code) {
			this.code = code;
		}

		@Override
		public int hashCode() {
			return Objects.hash(code, name);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Country other = (Country) obj;
			return Objects.equals(code, other.code) && Objects.equals(name, other.name);
		}
	}
	
	public final static class Address {
		private String streetAddress;
		private String city;
		private String county;
		private Country country;
		
		public Address() {
			
		}
		
		public Address(String streetAddress, String city, String county, Country country) {
			super();
			this.streetAddress = streetAddress;
			this.city = city;
			this.county = county;
			this.country = country;
		}

		@Override
		public int hashCode() {
			return Objects.hash(city, country, county, streetAddress);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Address other = (Address) obj;
			return Objects.equals(city, other.city) && Objects.equals(country, other.country)
					&& Objects.equals(county, other.county) && Objects.equals(streetAddress, other.streetAddress);
		}
		
	}
	
	public final static class Item {
		private String key;
		private Long number;
		private byte[] data;
		
		public Item() {
		}
		
		public Item(String key, Long number) {
			super();
			this.key = key;
			this.number = number;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(data);
			result = prime * result + Objects.hash(number, key);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Item other = (Item) obj;
			return Objects.equals(number, other.number) && Arrays.equals(data, other.data)
					&& Objects.equals(key, other.key);
		}
		
		
	}
	
	public final static class Person {
		public String name;
		public int age;
		public boolean sendSpam;
		public Address address;
		public byte[] signature;
		public ByteBuffer payload;
		public String[] telephones;
		public FavouriteColour favouriteColour;
		
		@INIField(itemType = String.class)
		public Map<String, String> props = new HashMap<>();
		
		@INIField(itemType = Item.class)
		public List<Item> items = new ArrayList<>();
		
		public Person() {
		}
		
		public Person(String name, int age, Address address, boolean sendSpam, FavouriteColour favouriteColour, String... telephones) {
			super();
			this.favouriteColour = favouriteColour;
			this.telephones = telephones;
			this.name = name;
			this.age = age;
			this.address = address;
			this.sendSpam = sendSpam;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(signature);
			result = prime * result + Arrays.hashCode(telephones);
			result = prime * result + Objects.hash(address, age, items, name, payload, props, sendSpam);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Person other = (Person) obj;
			return Objects.equals(address, other.address) && age == other.age && Objects.equals(items, other.items)
					&& Objects.equals(name, other.name) && Objects.equals(payload, other.payload)
					&& Objects.equals(props, other.props) && sendSpam == other.sendSpam
					&& Arrays.equals(signature, other.signature) && Arrays.equals(telephones, other.telephones);
		}
	}

	protected Person createPerson() {
		var person = new Person("Joe B", 32, new Address("11 Some Road", "London", "Essex", UK), true, FavouriteColour.GREEN, "123 456789", "987 654321");
		person.props.put("Prop1", "Str1");
		person.props.put("Prop2", "Str2");
		person.signature = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05};
		person.payload = ByteBuffer.wrap(new byte[] { (byte)0xa0, (byte)0xb0, (byte)0xc0, (byte)0xd0, (byte)0xe0}); 
		
		person.items.add(new Item("An Orange", 123l));
		person.items.add(new Item("A Bag Of Dreamies", 456l));
		person.items.add(new Item("Fourteen Pairs Of Pink Slippers", 789l));
		return person;
	}
}
