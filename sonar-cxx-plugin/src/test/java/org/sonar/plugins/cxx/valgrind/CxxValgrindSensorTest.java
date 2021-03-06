/*
 * Sonar C++ Plugin (Community)
 * Copyright (C) 2010-2016 SonarOpenCommunity
 * http://github.com/SonarOpenCommunity/sonar-cxx
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
package org.sonar.plugins.cxx.valgrind;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.batch.SensorContext; //@todo deprecated
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.component.ResourcePerspectives; //@todo deprecated
import org.sonar.api.config.Settings;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.Project; //@todo deprecated
import org.sonar.plugins.cxx.TestUtils;

public class CxxValgrindSensorTest {

  private CxxValgrindSensor sensor;
  private SensorContext context;
  private Project project;
  private DefaultFileSystem fs;
  private Issuable issuable;
  private ResourcePerspectives perspectives;

  @Before
  public void setUp() {
    fs = TestUtils.mockFileSystem();
    project = TestUtils.mockProject();
    issuable = TestUtils.mockIssuable();
    perspectives = TestUtils.mockPerspectives(issuable);
    sensor = new CxxValgrindSensor(perspectives, new Settings(), fs, mock(RulesProfile.class));
    context = mock(SensorContext.class);
  }

  @Test
  public void shouldNotThrowWhenGivenValidData() {
    sensor.analyse(project, context);
  }

  @Test
  public void shouldSaveViolationIfErrorIsInside() {
    Set<ValgrindError> valgrindErrors = new HashSet<>();
    valgrindErrors.add(mockValgrindError(true));
    TestUtils.addInputFile(fs, perspectives, issuable, "dir/file");
    sensor.saveErrors(project, context, valgrindErrors);
    verify(issuable, times(1)).addIssue(any(Issue.class));
  }

  @Test
  public void shouldNotSaveViolationIfErrorIsOutside() {
    Set<ValgrindError> valgrindErrors = new HashSet<>();
    valgrindErrors.add(mockValgrindError(false));
    sensor.saveErrors(project, context, valgrindErrors);
    verify(issuable, times(0)).addIssue(any(Issue.class));
  }

  private ValgrindError mockValgrindError(boolean inside) {
    ValgrindError error = mock(ValgrindError.class);
    when(error.getKind()).thenReturn("valgrind-error");
    ValgrindFrame frame = inside == true ? generateValgrindFrame() : null;
    when(error.getLastOwnFrame((anyString()))).thenReturn(frame);
    return error;
  }

  private ValgrindFrame generateValgrindFrame() {
    return new ValgrindFrame("ip", "obj", "fn", "dir", "file", "1");
  }
}
