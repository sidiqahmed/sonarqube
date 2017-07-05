/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.scanner.config;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.Encryption;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

public class DefaultConfigurationTest {

  @Rule
  public LogTester logTester = new LogTester();

  @Test
  public void accessingMultiValuedPropertiesShouldBeConsistentWithDeclaration() {
    Configuration config = new DefaultConfiguration(new PropertyDefinitions(Arrays.asList(
      PropertyDefinition.builder("single").multiValues(false).build(),
      PropertyDefinition.builder("multiA").multiValues(true).build())), new Encryption(null),
      mock(AnalysisMode.class),
      ImmutableMap.of("single", "foo", "multiA", "a,b", "notDeclared", "c,d")) {
    };

    assertThat(config.get("multiA")).hasValue("a,b");
    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("Access to the multi-valued property 'multiA' should be made using 'getStringArray' method. The SonarQube plugin using this property should be updated.");

    logTester.clear();

    assertThat(config.getStringArray("single")).containsExactly("foo");
    assertThat(logTester.logs(LoggerLevel.WARN))
      .contains("Property 'single' is not declared as multi-valued but was read using 'getStringArray' method. The SonarQube plugin declaring this property should be updated.");

    logTester.clear();

    assertThat(config.get("notDeclared")).hasValue("c,d");
    assertThat(config.getStringArray("notDeclared")).containsExactly("c", "d");
    assertThat(logTester.logs(LoggerLevel.WARN)).isEmpty();
  }

  @Test
  public void getDefaultValues() {
    Configuration config = new DefaultConfiguration(new PropertyDefinitions(Arrays.asList(
      PropertyDefinition.builder("single").multiValues(false).defaultValue("default").build(),
      PropertyDefinition.builder("multiA").multiValues(true).defaultValue("foo,bar").build())), new Encryption(null),
      mock(AnalysisMode.class),
      ImmutableMap.of()) {
    };

    assertThat(config.get("multiA")).hasValue("foo,bar");
    assertThat(config.getStringArray("multiA")).containsExactly("foo", "bar");
    assertThat(config.get("single")).hasValue("default");
    assertThat(config.getStringArray("single")).containsExactly("default");
  }

  @Test
  public void testParsingMultiValues() {
    assertThat(getStringArray("")).isEmpty();
    assertThat(getStringArray(",")).containsExactly("", "");
    assertThat(getStringArray(",,")).containsExactly("", "", "");
    assertThat(getStringArray("a")).containsExactly("a");
    assertThat(getStringArray("a b")).containsExactly("a b");
    assertThat(getStringArray("a , b")).containsExactly("a", "b");
    assertThat(getStringArray("\"a \",\" b\"")).containsExactly("a ", " b");
    assertThat(getStringArray("\"a,b\",c")).containsExactly("a,b", "c");
    try {
      getStringArray("\"a ,b");
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Property: 'multi' doesn't contain a valid CSV value: '\"a ,b'");
    }
  }

  private String[] getStringArray(String value) {
    return new DefaultConfiguration(new PropertyDefinitions(Arrays.asList(
      PropertyDefinition.builder("multi").multiValues(true).build())), new Encryption(null),
      mock(AnalysisMode.class),
      ImmutableMap.of("multi", value)) {
    }.getStringArray("multi");
  }
}