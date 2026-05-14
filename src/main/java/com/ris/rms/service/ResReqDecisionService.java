package com.ris.rms.service;

import java.util.List;
import java.util.Map;

public interface ResReqDecisionService {


	Map<String, Object> hrDecide(List<Long> requestId, Long approverUserId, String decision, String comments);

	Map<String, Object> hrDecideGroup(Long groupId, Long approverUserId, String decision, String comments);


}
