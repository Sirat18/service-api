/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.ta.reportportal.core.project.impl;

import com.epam.ta.reportportal.core.analyzer.LogIndexer;
import com.epam.ta.reportportal.core.analyzer.impl.AnalyzerStatusCache;
import com.epam.ta.reportportal.core.events.MessageBus;
import com.epam.ta.reportportal.core.events.activity.ProjectIndexEvent;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.dao.UserRepository;
import com.epam.ta.reportportal.entity.attribute.Attribute;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.entity.project.ProjectAttribute;
import com.epam.ta.reportportal.entity.user.User;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * @author <a href="mailto:ihar_kahadouski@epam.com">Ihar Kahadouski</a>
 */
@ExtendWith(MockitoExtension.class)
class DeleteProjectHandlerImplTest {

	@Mock
	private ProjectRepository projectRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private LogIndexer logIndexer;

	@Mock
	private AnalyzerStatusCache analyzerStatusCache;

	@Mock
	private MessageBus messageBus;

	@InjectMocks
	private DeleteProjectHandlerImpl handler;

	@Test
	void deleteNotExistProject() {
		String projectName = "notExist";
		when(projectRepository.findByName(projectName)).thenReturn(Optional.empty());

		ReportPortalException exception = assertThrows(ReportPortalException.class, () -> handler.deleteProject(projectName));

		assertEquals("Project 'notExist' not found. Did you use correct project name?", exception.getMessage());
	}

	@Test
	void deleteIndexOnNotExistProject() {
		String projectName = "notExist";
		when(projectRepository.findByName(projectName)).thenReturn(Optional.empty());

		ReportPortalException exception = assertThrows(ReportPortalException.class, () -> handler.deleteProjectIndex(projectName, "user"));

		assertEquals("Project 'notExist' not found. Did you use correct project name?", exception.getMessage());
	}

	@Test
	void deleteProjectIndexByNotExistUser() {
		String projectName = "notExist";
		String userName = "user";
		when(projectRepository.findByName(projectName)).thenReturn(Optional.of(new Project()));
		when(userRepository.findByLogin(userName)).thenReturn(Optional.empty());

		ReportPortalException exception = assertThrows(ReportPortalException.class, () -> handler.deleteProjectIndex(projectName, "user"));

		assertEquals("User 'user' not found.", exception.getMessage());
	}

	@Test
	void deleteIndexWhenIndexingRunning() {
		String projectName = "test_project";
		String userName = "user";
		Long projectId = 1L;
		when(projectRepository.findByName(projectName)).thenReturn(Optional.of(getProjectWithAnalyzerAttributes(projectId, true)));
		when(userRepository.findByLogin(userName)).thenReturn(Optional.of(new User()));

		ReportPortalException exception = assertThrows(ReportPortalException.class, () -> handler.deleteProjectIndex(projectName, "user"));

		assertEquals("Forbidden operation. Index can not be removed until index generation proceeds.", exception.getMessage());
	}

	@Test
	void deleteIndexWhenIndexingCacheNotInvalidated() {
		String projectName = "test_project";
		String userName = "user";
		Long projectId = 1L;
		when(projectRepository.findByName(projectName)).thenReturn(Optional.of(getProjectWithAnalyzerAttributes(projectId, false)));
		when(userRepository.findByLogin(userName)).thenReturn(Optional.of(new User()));
		Cache<Long, Long> cache = CacheBuilder.newBuilder().build();
		cache.put(2L, projectId);
		when(analyzerStatusCache.getAnalyzeStatus()).thenReturn(cache);

		ReportPortalException exception = assertThrows(ReportPortalException.class, () -> handler.deleteProjectIndex(projectName, "user"));

		assertEquals("Forbidden operation. Index can not be removed until index generation proceeds.", exception.getMessage());
	}

	@Test
	void happyDeleteIndex() {
		String projectName = "test_project";
		String userName = "user";
		Long projectId = 1L;
		Project project = getProjectWithAnalyzerAttributes(projectId, false);
		project.setName(projectName);
		when(projectRepository.findByName(projectName)).thenReturn(Optional.of(project));
		when(userRepository.findByLogin(userName)).thenReturn(Optional.of(new User()));
		when(analyzerStatusCache.getAnalyzeStatus()).thenReturn(CacheBuilder.newBuilder().build());

		OperationCompletionRS response = handler.deleteProjectIndex(projectName, "user");

		verify(logIndexer, times(1)).deleteIndex(projectId);
		verify(messageBus, times(1)).publishActivity(any(ProjectIndexEvent.class));

		assertEquals(response.getResultMessage(), "Project index with name = '" + projectName + "' is successfully deleted.");

	}

	private Project getProjectWithAnalyzerAttributes(Long projectId, boolean indexingRunning) {
		Project project = new Project();
		project.setProjectAttributes(Sets.newHashSet(
				getProjectAttribute(project, getAttribute("analyzer.isAutoAnalyzerEnabled"), "false"),
				getProjectAttribute(project, getAttribute("analyzer.minDocFreq"), "7"),
				getProjectAttribute(project, getAttribute("analyzer.minTermFreq"), "2"),
				getProjectAttribute(project, getAttribute("analyzer.minShouldMatch"), "80"),
				getProjectAttribute(project, getAttribute("analyzer.numberOfLogLines"), "5"),
				getProjectAttribute(project, getAttribute("analyzer.indexingRunning"), String.valueOf(indexingRunning))
		));
		project.setId(projectId);
		return project;
	}

	private ProjectAttribute getProjectAttribute(Project project, Attribute attribute, String value) {
		return new ProjectAttribute().withProject(project).withAttribute(attribute).withValue(value);
	}

	private Attribute getAttribute(String name) {
		Attribute attribute = new Attribute();
		attribute.setName(name);
		return attribute;
	}
}