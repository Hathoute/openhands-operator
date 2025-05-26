# OpenHands Kubernetes Operator

`openhands-operator` is a Kubernetes operator that eases the execution of [OpenHands](https://github.com/All-Hands-AI/OpenHands) in a Kubernetes Cluster.


## Features

* **LLM Resource**: Define and manage LLM endpoints declaratively.
* **LLMTask Resource**: Automate prompts, scripts, and pod executions using LLMs.
* **Helm Deployment**: Easily install the operator with Helm.
* **Custom Pod Behavior**: Customize task execution environments with full `podSpec` control.
* **Script Hooks**: Integrate pre and post scripts around your prompts.


## Installation

### 1. Install CRDs

```bash
kubectl apply -f https://github.com/Hathoute/openhands-operator/releases/download/0.0.3/llms.com.hathoute.kubernetes-v1.yml
kubectl apply -f https://github.com/Hathoute/openhands-operator/releases/download/0.0.3/llmtasks.com.hathoute.kubernetes-v1.yml
```

> Replace `0.0.3` with your desired version tag if needed.


### 2. Install the Operator via Helm

```bash
helm pull oci://ghcr.io/hathoute/charts/openhands-operator --version 0.0.3
helm install openhands-operator ./openhands-operator-0.0.3.tgz
```

- Make sure to configure the watched namespaces if you'd like to restrict the operator to
specific namespaces inside the cluster.
- Make sure to create the appropriate RBAC for the operator if you opt out of automatic RBAC creation.


## Example Usage

Create and apply the following resources to get started:

`llm.yaml:`
```yaml
apiVersion: com.hathoute.kubernetes/v1alpha1
kind: LLM
metadata:
  name: gemini
  namespace: watched-namespace
spec:
  modelName: gemini/gemini-2.0-flash
  apiKey: your-gemini-key-here
```

`llm-task.yaml:`
```yaml
apiVersion: com.hathoute.kubernetes/v1alpha1
kind: LLMTask
metadata:
  name: fix-issue-1234
  namespace: watched-namespace
spec:
  # The name of the LLM resource to use (must be in the same namespace)
  llmName: gemini
  # (Optional) Your preScript in bash
  preScript: |
    echo "This will run before invoking OpenHands"
  prompt: "OpenHands prompt here"
  # (Optional) Your postScript in bash
  postScript: |
    echo "This will run after invoking OpenHands"
  podSpec:
    # (Optional) Override default pod spec ('openhands' is the 
    # container to run your LLM task)
    securityContext:
      fsGroup: 1000
    containers:
      - name: openhands
        volumeMounts:
          - name: workspace
            mountPath: /workspace
            readOnly: false
        env:
          - name: DEBUG
            value: "1"
    volumes:
      - name: workspace
        emptyDir:
          sizeLimit: 1Gi
```

OpenHands will be configured using the `LLM` resource invoked using the following command:

```bash
poetry run python -m openhands.core.main -t "LLMTask.spec.prompt"
```

## License

Licensed under the MIT License. See [LICENSE](LICENSE) for details.
