import torch
from datasets import load_dataset
from torch.utils.data import DataLoader, Dataset

APP_TYPES = [""]
CATEGORIES = [""]


class LangCache(Dataset):
    def __init__(
        self,
        app_types=APP_TYPES,
        categories=CATEGORIES,
        mod=10**16 + 61,
        multiplier=370984,
    ):
        super(LangCache).__init__()
        self.app_types = app_types
        self.categories = categories
        self.ds = load_dataset("redis/langcache-sentencepairs-v1", "all")["train"]
        self.size = len(self.ds)
        self.mod = mod
        self.multiplier = multiplier

    def __len__(self):
        return 2 * self.size

    def __getitem__(self, index):
        item = self.ds[index % len(self)]

        same = 2 * index < len(self)

        notification_1 = {
            "appinfo": self.app_types[index % len(self.app_types)],
            "title": "",
            "text": item["sentence1"],
            "bigText": "",
            "category": self.categories[index % len(self.categories)],
        }

        if not same:
            index = (self.multiplier * index) % len(self)
            item = self.ds[index % len(self)]

        notification_2 = {
            "appinfo": self.app_types[index % len(self.app_types)],
            "title": "",
            "text": item["sentence2"],
            "bigText": "",
            "category": self.categories[index % len(self.categories)],
        }

        y = float(item["label"])
        return (notification_1, notification_2), y
