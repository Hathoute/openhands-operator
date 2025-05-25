package com.hathoute.kubernetes.operator.openhands.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Singular("llm")
@Kind(LLMResource.KIND)
@Group(LLMResource.GROUP)
@Version(LLMResource.VERSION)
public class LLMResource extends CustomResource<LLMSpec, LLMStatus> implements Namespaced {

  public static final String GROUP = "com.hathoute.kubernetes";
  public static final String VERSION = "v1alpha1";
  public static final String APIVERSION = "%s/%s".formatted(GROUP, VERSION);
  public static final String KIND = "LLM";

}
