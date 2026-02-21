# %%
import torch
from torch import nn, optim
from torch.utils.data import Dataset, DataLoader
import torch.nn.functional as F
from sentence_transformers import SentenceTransformer
from data_sets import LockIn, CustomerSupportTicketsPriority, CustomerSupportTicketsSimilarity, NotifyAI, LangCache
from tqdm import tqdm

# %%
# Load model directly
from transformers import AutoModel, AutoTokenizer

model = SentenceTransformer("sentence-transformers/all-roberta-large-v1")

# %%
tokenizer = AutoTokenizer.from_pretrained(
    "sentence-transformers/all-MiniLM-L6-v2"
)

roberta = AutoModel.from_pretrained(
    "sentence-transformers/all-MiniLM-L6-v2"
)
roberta

# %%
tokenizer(["hej jag heter Julius", "Hej vi bygger en cool app"])


# %%
class Attention(nn.Module):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

        self.roberta = AutoModel.from_pretrained(
            "sentence-transformers/all-MiniLM-L6-v2"
        )

        self.latent = nn.Sequential(
            nn.Linear(384, 384),
            nn.GELU(),
            nn.Linear(384, 384),
        )
        
        self.similarity = nn.Sequential(
            nn.Linear(384*2, 384),
            nn.GELU(),
            nn.Linear(384, 64),
            nn.GELU(),
            nn.Linear(64, 1)
        )
        
        self.priority = nn.Sequential(
            nn.Linear(384, 384),
            nn.GELU(),
            nn.Linear(384, 64),
            nn.GELU(),
            nn.Linear(64, 1)
        )
        
        self.sigmoid = nn.Sigmoid()
        
    def freeze_roberta(self):
        for module in self.roberta.parameters():
            module.requires_grad = False
    
    def get_embeddings(self, input_ids: torch.Tensor, attention_mask: torch.Tensor, **kwarg) -> torch.Tensor:
        outputs = self.roberta(
            input_ids=input_ids,
            attention_mask=attention_mask
        )
        # mean pooling
        last_hidden = outputs.last_hidden_state
        mask = attention_mask.unsqueeze(-1)
        summed = (last_hidden * mask).sum(1)
        counts = mask.sum(1)
        return summed / counts
        
    def forward(self, input_ids, attention_mask, Gx: torch.Tensor = None, return_latent=True, **kwargs):
        latent = self.get_embeddings(input_ids, attention_mask)
        priority = self.priority(latent)
        similarity = None

        if Gx is not None:
            B = latent.shape[0]
            if len(Gx.shape) == 2:
                Gx = Gx.unsqueeze(0)
                Gx = Gx.expand(B, -1, -1)
            K = Gx.shape[1]
            
            x = latent.unsqueeze(1).expand(-1, K, -1)
            
            xGx = torch.concat([x, Gx], dim=-1)
            similarity = self.sigmoid(self.similarity(xGx))

        if return_latent:
            return priority, similarity, latent
        return priority, similarity # (B, 1), (B, K, 1)


# %%
def inf_iter(itr):
    while 1:
        for item in itr:
            yield item

# %%
def preprosses_priority(datapoint):
    return ":".join([dp if dp is not None else "" for dp in datapoint.values()])

def preprosses_similarity(datapoints):
    return tuple(map(preprosses_priority, datapoints))

def collate_fn_priority(data):
    return [first for first, _  in data], torch.tensor([second for _, second  in data]).unsqueeze(-1)

def collate_fn_similarity(data):
    return tuple(zip(*[first for first, _  in data])) , torch.tensor([second for _, second  in data]).unsqueeze(-1)

# %%
def train():
    
    device = 'cpu'
    
    ds_priority = LockIn([CustomerSupportTicketsPriority(), NotifyAI()], preprocess=preprosses_priority)
    ds_similarity = LockIn([CustomerSupportTicketsSimilarity(), LangCache()], preprocess=preprosses_similarity)
    
    dl_priority = DataLoader(ds_priority, batch_size=128, shuffle=True, collate_fn=collate_fn_priority)
    dl_similarity = inf_iter(DataLoader(ds_similarity, batch_size=128, shuffle=True, collate_fn=collate_fn_similarity))
    
    model = Attention()
    model.freeze_roberta()
    
    tokenizer = AutoTokenizer.from_pretrained(
        "sentence-transformers/all-MiniLM-L6-v2"
    )
    
    opt = optim.AdamW(model.parameters(), lr=0.0001)
    loss_fn_priority = nn.MSELoss()
    loss_fn_similarity = nn.BCELoss()
    
    bar = tqdm(total=len(ds_priority), desc="Epoch")
    
    for sentences, y in dl_priority:
        y = y.to(device)
        encode = tokenizer(sentences, padding=True, truncation=True, return_tensors='pt')
        
        priority, _ = model(**encode, return_latent=False)
        loss: torch.Tensor = loss_fn_priority(priority, y)
        
        (sent1, sent2), y = next(dl_similarity)
        sent_1_encode = tokenizer(sent1, padding=True, truncation=True, return_tensors='pt')
        sent_1_emb = model.get_embeddings(**sent_1_encode).unsqueeze(1) # (B, 1, 384)
        sent_2_encode = tokenizer(sent2, padding=True, truncation=True, return_tensors='pt')
        _, similarity = model(**sent_2_encode, Gx=sent_1_emb, return_latent=False) # (B, 1, 1)
        loss += loss_fn_similarity(similarity.squeeze(-1), y)
        
        loss.backward()
        opt.step()
        opt.zero_grad()
        
        bar.update()
        print("one iteration")
        
        
        

    

# %%
train()

# %%
ds_priority = LockIn([CustomerSupportTicketsPriority(), NotifyAI()], preprocess=preprosses_priority)
ds_similarity = LockIn([CustomerSupportTicketsSimilarity(), LangCache()], preprocess=preprosses_similarity)

dl_priority = DataLoader(ds_priority, batch_size=128, shuffle=True, collate_fn=collate_fn_priority)
dl_similarity = inf_iter(DataLoader(ds_similarity, batch_size=128, shuffle=True, collate_fn=collate_fn_similarity))

# %%
len(ds_priority), len(ds_similarity)

# %%
from datasets import load_dataset
ds = load_dataset("charlesfeng1/notifai-dataset")['train']

# %%
ds[torch.tensor(2996).item()]

# %%
torch.tensor(2996)

# %%



