/*
 * Copyright 2018 EPAM Systems
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

import com.epam.ta.reportportal.core.events.attachment.DeleteProjectAttachmentsEvent;
import com.epam.ta.reportportal.core.project.DeleteProjectHandler;
import com.epam.ta.reportportal.dao.ProjectRepository;
import com.epam.ta.reportportal.entity.project.Project;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.BulkRQ;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.project.DeleteProjectRQ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Pavel Bortnik
 */
@Service
public class DeleteProjectHandlerImpl implements DeleteProjectHandler {

	private final ProjectRepository projectRepository;

	private final ApplicationEventPublisher eventPublisher;

	@Autowired
	public DeleteProjectHandlerImpl(ProjectRepository projectRepository, ApplicationEventPublisher eventPublisher) {
		this.projectRepository = projectRepository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public OperationCompletionRS deleteProject(String projectName) {
		Project project = projectRepository.findByName(projectName)
				.orElseThrow(() -> new ReportPortalException(ErrorType.PROJECT_NOT_FOUND, projectName));

		projectRepository.deleteById(project.getId());

		eventPublisher.publishEvent(new DeleteProjectAttachmentsEvent(project.getId()));

		return new OperationCompletionRS("Project with name = '" + projectName + "' has been successfully deleted.");
	}

	@Override
	public OperationCompletionRS deleteProjectIndex(String projectName, String username) {
		return null;
	}

	@Override
	public List<OperationCompletionRS> deleteProjects(BulkRQ<DeleteProjectRQ> deleteProjectBulkRQ) {
		return deleteProjectBulkRQ.getEntities()
				.values()
				.stream()
				.map(DeleteProjectRQ::getProjectName)
				.map(this::deleteProject)
				.collect(Collectors.toList());
	}
}
