package edu.campus.taskplatform.complaint;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.complaint.dto.CreateComplaintRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

	private final ComplaintService service;

	public ComplaintController(ComplaintService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ComplaintDto> create(@Valid @RequestBody CreateComplaintRequest request) {
		return ApiResponse.ok(service.create(request));
	}
}
