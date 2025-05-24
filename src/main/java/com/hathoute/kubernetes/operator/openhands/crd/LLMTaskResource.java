package com.hathoute.kubernetes.operator.openhands.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Singular("llmtask")
@Kind("LLMTask")
@Group("com.hathoute.kubernetes")
@Version("v1alpha1")
public class LLMTaskResource extends CustomResource<LLMTaskSpec, LLMTaskStatus> implements Namespaced {

}
