"""Export the official CubiCasa checkpoint; PyTorch is needed only for this offline step.

Source: https://github.com/CubiCasa/CubiCasa5k
Reference commit: c34440266665a11f4484eb06cd2e4b7d72ad76c1
Checkpoint SHA256: dd20b4e1bf1d670f2125107b079df06958b1ccd36e49a464ab739aeb00b8e7a2
The upstream model/data license is CC BY-NC 4.0. Weights are not redistributed here.
"""
import argparse
import hashlib
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    expected = "dd20b4e1bf1d670f2125107b079df06958b1ccd36e49a464ab739aeb00b8e7a2"
    if hashlib.sha256(args.checkpoint.read_bytes()).hexdigest() != expected:
        parser.error("Checkpoint checksum does not match the official evaluated model")
    sys.path.insert(0, str(args.source.resolve()))
    import torch
    from floortrans.models.hg_furukawa_original import hg_furukawa_original

    model = hg_furukawa_original(51)
    model.conv4_ = torch.nn.Conv2d(256, 44, kernel_size=1)
    model.upsample = torch.nn.ConvTranspose2d(44, 44, kernel_size=4, stride=4)
    checkpoint = torch.load(args.checkpoint, map_location="cpu", weights_only=True)
    model.load_state_dict(checkpoint["model_state"])
    model.eval()

    class SemanticOutput(torch.nn.Module):
        def __init__(self, network):
            super().__init__()
            self.network = network

        def forward(self, image):
            return self.network(image)[:, 21:]  # 12 room/boundary classes and 11 icon classes

    args.output.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(SemanticOutput(model).eval(), torch.zeros(1, 3, 512, 640), args.output,
                      input_names=["image"], output_names=["logits"],
                      dynamic_axes={"image": {2: "height", 3: "width"},
                                    "logits": {2: "height", 3: "width"}},
                      opset_version=17, dynamo=False)
    print(f"Exported {args.output}")


if __name__ == "__main__":
    main()
