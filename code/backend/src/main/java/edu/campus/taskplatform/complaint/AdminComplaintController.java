package edu.campus.taskplatform.complaint;

import java.util.UUID;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.complaint.dto.ComplaintActionRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/complaints")
public class AdminComplaintController {

	private final ComplaintService service;

	public AdminComplaintController(ComplaintService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<PageResult<ComplaintDto>> list(
			@RequestParam(required = false) ComplaintStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(service.list(status, page, size));
	}

	@PostMapping("/{complaintId}/actions")
	public ApiResponse<ComplaintDto> process(@PathVariable UUID complaintId,
			@Valid @RequestBody ComplaintActionRequest request) {
		return ApiResponse.ok(service.process(complaintId, request));
	}
}
